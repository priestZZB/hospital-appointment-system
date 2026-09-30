package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.entity.ResultItem;
import com.hospital.medsupply.entity.Specimen;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ExamReportMapper;
import com.hospital.medsupply.mapper.ResultItemMapper;
import com.hospital.medsupply.mapper.SpecimenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 正式化验单 PDF 服务（迭代8 C4）
 * <p>
 * pdfbox 生成：头部（医院名/标本号/患者ID/申请项目/采样时间/报告时间）+
 * 结果表格（项目/代号/结果/单位/参考范围，↑↓ 异常行红字）+ 底部检验者/审核者。
 * 中文字体加载与 clinic 医疗证明/用药指导单一致（TTC 经 TrueTypeCollection.processAllFonts，
 * 且 TTC 不能提前 close——PDDocument.save() 时仍需读取字体数据流）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabReportPdfService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 异常行红字 RGB 192,0,0 */
    private static final int ABNORMAL_R = 192;
    private static final int ABNORMAL_G = 0;
    private static final int ABNORMAL_B = 0;

    private final ExamReportMapper examReportMapper;
    private final ResultItemMapper resultItemMapper;
    private final ExamApplicationMapper examApplicationMapper;
    private final SpecimenMapper specimenMapper;

    /**
     * 生成正式化验单 PDF 字节数组
     */
    public byte[] generatePdf(Long reportId) {
        if (reportId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "reportId 不能为空");
        }
        ExamReport report = examReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "检验报告不存在");
        }
        List<ResultItem> items = resultItemMapper.selectByReportId(reportId);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "该报告暂无检验结果明细，无法生成化验单");
        }
        ExamApplication application = report.getApplicationId() == null
                ? null : examApplicationMapper.selectById(report.getApplicationId());
        Specimen specimen = report.getApplicationId() == null
                ? null : specimenMapper.selectByApplicationId(report.getApplicationId())
                .stream().findFirst().orElse(null);

        try (PDDocument document = new PDDocument()) {
            PDType0Font fontChinese = loadChineseFont(document);
            PDPage page = newPage(document);
            PDPageContentStream cs = new PDPageContentStream(document, page);
            float y = 780;

            try {
                y = drawHead(cs, fontChinese, y, report, application, specimen);
                y = drawTableHeader(cs, fontChinese, y);
                for (ResultItem item : items) {
                    if (y < 130) {
                        cs.close();
                        page = newPage(document);
                        cs = new PDPageContentStream(document, page);
                        y = 770;
                        y = drawTableHeader(cs, fontChinese, y);
                    }
                    y = drawResultRow(cs, fontChinese, y, item);
                }
                y = drawFooter(cs, fontChinese, y, report);
            } finally {
                cs.close();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("[化验单] PDF 生成成功: reportId={}, rows={}, size={}", reportId, items.size(), baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[化验单] PDF 生成失败: reportId={}", reportId, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "化验单 PDF 生成失败");
        }
    }

    private PDPage newPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return page;
    }

    /** 头部：医院名标题 + 标本号/患者ID/申请项目/采样时间/报告时间 */
    private float drawHead(PDPageContentStream cs, PDType0Font font, float y, ExamReport report,
                           ExamApplication application, Specimen specimen) throws IOException {
        cs.beginText();
        setFont(cs, font, 20);
        cs.newLineAtOffset(180, y);
        cs.showText("示例医院 检验报告单");
        cs.endText();

        y -= 30;
        cs.setLineWidth(1f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();

        y -= 24;
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText("标本号: " + (specimen != null && specimen.getSpecimenNo() != null
                ? specimen.getSpecimenNo() : "-")
                + "    患者ID: " + (report.getPatientId() != null ? report.getPatientId() : "-"));
        cs.endText();

        y -= 18;
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText("申请项目: " + (application != null && application.getExamItemName() != null
                ? application.getExamItemName() : "-"));
        cs.endText();

        y -= 18;
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText("采样时间: " + (specimen != null && specimen.getCollectTime() != null
                ? specimen.getCollectTime().format(DATE_TIME) : "-")
                + "    报告时间: " + (report.getCompleteTime() != null
                ? report.getCompleteTime().format(DATE_TIME)
                : report.getCreateTime() != null ? report.getCreateTime().format(DATE_TIME) : "-"));
        cs.endText();

        y -= 12;
        cs.setLineWidth(0.6f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();
        return y - 14;
    }

    /** 表格头：项目/代号/结果/单位/参考范围 */
    private float drawTableHeader(PDPageContentStream cs, PDType0Font font, float y) throws IOException {
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText("项目");
        cs.endText();
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(205, y);
        cs.showText("代号");
        cs.endText();
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(270, y);
        cs.showText("结果");
        cs.endText();
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(350, y);
        cs.showText("单位");
        cs.endText();
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(415, y);
        cs.showText("参考范围");
        cs.endText();

        y -= 8;
        cs.setLineWidth(0.8f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();
        return y - 16;
    }

    /** 结果行：异常（↑/↓）行用红字 */
    private float drawResultRow(PDPageContentStream cs, PDType0Font font, float y, ResultItem item) throws IOException {
        boolean abnormal = item.getAbnormalFlag() != null && !item.getAbnormalFlag().isBlank();
        String value = item.getResultValue() + (abnormal ? item.getAbnormalFlag() : "");

        if (abnormal) {
            cs.setNonStrokingColor(ABNORMAL_R, ABNORMAL_G, ABNORMAL_B);
        }
        drawCell(cs, font, 60, y, abbreviate(item.getItemName(), 11));
        drawCell(cs, font, 205, y, abbreviate(item.getItemCode(), 10));
        drawCell(cs, font, 270, y, abbreviate(value, 14));
        drawCell(cs, font, 350, y, abbreviate(item.getUnit(), 7));
        drawCell(cs, font, 415, y, abbreviate(item.getRefRange(), 16));
        if (abnormal) {
            cs.setNonStrokingColor(0, 0, 0);
        }

        y -= 6;
        cs.setLineWidth(0.3f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();
        return y - 12;
    }

    /** 底部：检验者/审核者 + 打印时间 */
    private float drawFooter(PDPageContentStream cs, PDType0Font font, float y, ExamReport report) throws IOException {
        y -= 14;
        cs.setLineWidth(0.8f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();

        y -= 24;
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText("检验者: " + (report.getOperatorId() != null ? report.getOperatorId() : "-")
                + "    审核者: " + (report.getAuditorId() != null ? report.getAuditorId() : "-")
                + "    打印时间: " + LocalDateTime.now().format(DATE_TIME));
        cs.endText();
        return y;
    }

    private void drawCell(PDPageContentStream cs, PDType0Font font, float x, float y, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        cs.beginText();
        setFont(cs, font, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void setFont(PDPageContentStream cs, PDType0Font font, float size) throws IOException {
        if (font != null) {
            cs.setFont(font, size);
        } else {
            cs.setFont(PDType1Font.HELVETICA, size);
        }
    }

    /** 中文按字符数截断（表格列宽有限，超长省略号收尾） */
    private static String abbreviate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() > maxChars ? text.substring(0, maxChars - 1) + "…" : text;
    }

    private PDType0Font loadChineseFont(PDDocument document) {
        // .ttc 字体集合必须经 TrueTypeCollection 加载（直接 PDType0Font.load(File) 会抛
        // RandomAccessFile 异常）；TTF 单文件可直接加载。
        String[] candidates = {
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simkai.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        };
        for (String path : candidates) {
            File f = new File(path);
            if (!f.exists()) {
                continue;
            }
            try {
                if (path.endsWith(".ttc")) {
                    // 注意：TTC 不能提前 close——PDDocument.save() 时仍需读取字体数据流；
                    // fontbox 2.0.x 的 getFontAtIndex 为 private，用 processAllFonts 取首个字体
                    org.apache.fontbox.ttf.TrueTypeCollection ttc =
                            new org.apache.fontbox.ttf.TrueTypeCollection(f);
                    final org.apache.fontbox.ttf.TrueTypeFont[] holder = new org.apache.fontbox.ttf.TrueTypeFont[1];
                    ttc.processAllFonts(ttf -> {
                        if (holder[0] == null) {
                            holder[0] = ttf;
                        }
                    });
                    return PDType0Font.load(document, holder[0], true);
                }
                return PDType0Font.load(document, f);
            } catch (Exception e) {
                log.warn("[化验单] 字体加载失败: {}", path);
            }
        }
        log.warn("[化验单] 未找到系统 CJK 字体，中文将无法嵌入 PDF");
        return null;
    }
}
