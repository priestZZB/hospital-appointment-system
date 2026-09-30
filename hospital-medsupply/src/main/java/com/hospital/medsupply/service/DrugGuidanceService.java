package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PrescriptionFeignClient;
import com.hospital.medsupply.entity.Drug;
import com.hospital.medsupply.mapper.DrugMapper;
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
import java.util.Map;

/**
 * 用药指导单服务
 * <p>
 * 按处方明细生成 PDF 用药指导单（药名/规格/用法用量/频次/天数/注意事项），
 * 中文字体加载与 clinic 医疗证明一致（TTC 经 TrueTypeCollection.processAllFonts，
 * 且 TTC 不能提前 close——PDDocument.save() 时仍需读取字体数据流）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugGuidanceService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PrescriptionFeignClient prescriptionFeignClient;
    private final DrugMapper drugMapper;

    /**
     * 生成用药指导单 PDF 字节数组
     */
    public byte[] generatePdf(Long prescriptionId) {
        if (prescriptionId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "prescriptionId 不能为空");
        }
        List<Map<String, Object>> items = prescriptionFeignClient.getItems(prescriptionId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "处方不存在或无明细");
        }

        try (PDDocument document = new PDDocument()) {
            PDType0Font fontChinese = loadChineseFont(document);
            PDPage page = newPage(document);
            PDPageContentStream cs = new PDPageContentStream(document, page);
            float y = 770;

            try {
                y = drawTitle(cs, fontChinese, y, prescriptionId);
                for (Map<String, Object> item : items) {
                    if (y < 110) {
                        cs.close();
                        page = newPage(document);
                        cs = new PDPageContentStream(document, page);
                        y = 770;
                    }
                    y = drawItem(cs, fontChinese, y, item);
                }
                if (y > 90) {
                    y = drawFooter(cs, fontChinese, y);
                }
            } finally {
                cs.close();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("[用药指导单] PDF 生成成功: prescriptionId={}, size={}", prescriptionId, baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[用药指导单] PDF 生成失败: prescriptionId={}", prescriptionId, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "用药指导单 PDF 生成失败");
        }
    }

    private PDPage newPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return page;
    }

    private float drawTitle(PDPageContentStream cs, PDType0Font font, float y, Long prescriptionId) throws IOException {
        cs.beginText();
        setFont(cs, font, 22);
        cs.newLineAtOffset(190, y);
        cs.showText("医院用药指导单");
        cs.endText();

        y -= 36;
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();

        y -= 26;
        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(60, y);
        cs.showText("处方编号: RX-" + prescriptionId
                + "    打印时间: " + LocalDateTime.now().format(DATE_TIME));
        cs.endText();
        return y - 16;
    }

    private float drawItem(PDPageContentStream cs, PDType0Font font, float y, Map<String, Object> item) throws IOException {
        Drug drug = resolveDrug(item);
        String drugName = str(item.get("drugName"), drug != null ? drug.getDrugName() : null, "未知药品");
        String specification = str(item.get("specification"), drug != null ? drug.getSpecification() : null, "");
        String dosage = str(item.get("dosage"), null, "遵医嘱");
        String usageMethod = str(item.get("usageMethod"), null, "");
        String frequency = str(item.get("frequency"), null, "");
        String unit = str(item.get("unit"), null, "");
        String quantity = str(item.get("quantity"), null, "");
        String days = str(item.get("days"), null, "");
        String notice = drug != null && drug.getDescription() != null && !drug.getDescription().isBlank()
                ? drug.getDescription() : "请遵医嘱服用，如有不适及时就医。";

        cs.beginText();
        setFont(cs, font, 13);
        cs.newLineAtOffset(60, y);
        cs.showText("药品名称: " + drugName + (specification.isEmpty() ? "" : "（" + specification + "）"));
        cs.endText();
        y -= 20;

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(72, y);
        cs.showText("用法用量: 每次 " + dosage
                + (usageMethod.isEmpty() ? "" : "，" + usageMethod)
                + (frequency.isEmpty() ? "" : "，" + frequency)
                + (days.isEmpty() ? "" : "，共 " + days + " 天")
                + (quantity.isEmpty() ? "" : "，总量 " + quantity + unit));
        cs.endText();
        y -= 20;

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(72, y);
        cs.showText("注意事项: ");
        cs.endText();

        for (String line : wrap(notice, 38)) {
            y -= 18;
            if (y < 90) {
                break;
            }
            cs.beginText();
            setFont(cs, font, 11);
            cs.newLineAtOffset(86, y);
            cs.showText(line);
            cs.endText();
        }
        y -= 14;
        cs.moveTo(60, y);
        cs.lineTo(535, y);
        cs.setLineWidth(0.6f);
        cs.stroke();
        return y - 18;
    }

    private float drawFooter(PDPageContentStream cs, PDType0Font font, float y) throws IOException {
        cs.beginText();
        setFont(cs, font, 10);
        cs.newLineAtOffset(60, y - 30);
        cs.showText("请仔细阅读用药说明，按时按量服药；出现不良反应请立即停药并就医。");
        cs.endText();
        return y - 40;
    }

    private void setFont(PDPageContentStream cs, PDType0Font font, float size) throws IOException {
        if (font != null) {
            cs.setFont(font, size);
        } else {
            cs.setFont(PDType1Font.HELVETICA, size);
        }
    }

    private Drug resolveDrug(Map<String, Object> item) {
        Object drugId = item.get("drugId");
        if (drugId instanceof Number number) {
            return drugMapper.selectById(number.longValue());
        }
        return null;
    }

    private static String str(Object value, String fallback, String whenNull) {
        if (value != null && !value.toString().isBlank()) {
            return value.toString();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return whenNull;
    }

    /** 中文按固定宽度折行 */
    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new java.util.ArrayList<>();
        String remain = text;
        while (remain.length() > maxChars) {
            lines.add(remain.substring(0, maxChars));
            remain = remain.substring(maxChars);
        }
        lines.add(remain);
        return lines;
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
                log.warn("[用药指导单] 字体加载失败: {}", path);
            }
        }
        log.warn("[用药指导单] 未找到系统 CJK 字体，中文将无法嵌入 PDF");
        return null;
    }
}
