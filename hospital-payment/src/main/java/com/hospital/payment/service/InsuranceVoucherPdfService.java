package com.hospital.payment.service;

import cn.hutool.json.JSONUtil;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.payment.entity.InsuranceSettle;
import com.hospital.payment.mapper.InsuranceSettleMapper;
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
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 医保票据 PDF 服务（迭代11 H3，医疗收费票据样式）。
 * <p>
 * 版面：票据头「医疗收费票据（电子）」+ 票据号（=结算单号）+ 患者ID/医保号 +
 * 收费项目明细表（名称/类别/金额）+ 金额区（医保统筹支付/个人账户支付/现金支付/合计）+
 * 结算时间 +「医保结算专用章（模拟）」字样。
 * <p>
 * 字体链与既有 {@link PdfService} 一致（Windows 宋体 → Linux Noto CJK），
 * .ttc 集合经 TrueTypeCollection 加载（直接 PDType0Font.load(File) 会抛
 * RandomAccessFile 异常，见 medsupply LabReportPdfService 同款做法）；
 * 所有动态文本过 {@link #sanitizeGlyphs}，避免中文字体缺上标字形
 * （U+2070~U+2079/U+207B）导致 "No glyph" 整单失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceVoucherPdfService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InsuranceSettleMapper settleMapper;

    /**
     * 生成医疗收费票据（电子）PDF
     *
     * @param settleId 结算单 ID
     * @return PDF 字节数组
     */
    public byte[] generateVoucher(Long settleId) {
        InsuranceSettle settle = settleMapper.selectById(settleId);
        if (settle == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "医保结算单不存在");
        }
        List<Map<String, Object>> detail = parseDetail(settle.getDetailJson());

        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadChineseFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = drawHead(cs, font, settle);
                y = drawItemTable(cs, font, y, detail);
                y = drawAmountZone(cs, font, y, settle);
                y = drawFooter(cs, font, y, settle);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("[医保票据] PDF 生成成功: settleId={}, settleNo={}, size={}bytes",
                    settleId, settle.getSettleNo(), baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[医保票据] PDF 生成失败: settleId={}", settleId, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "医保票据 PDF 生成失败");
        }
    }

    // ==================== 版面绘制 ====================

    /** 票据头：标题 + 票据号 + 患者ID/医保号 */
    private float drawHead(PDPageContentStream cs, PDType0Font font, InsuranceSettle settle) throws IOException {
        cs.beginText();
        setFont(cs, font, 20);
        cs.newLineAtOffset(195, 780);
        cs.showText(sanitizeGlyphs("医疗收费票据（电子）"));
        cs.endText();

        cs.setLineWidth(1.2f);
        cs.moveTo(50, 760);
        cs.lineTo(545, 760);
        cs.stroke();

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(60, 738);
        cs.showText(sanitizeGlyphs("票据号: " + nvl(settle.getSettleNo())
                + "    业务类型: " + bizTypeText(settle.getBizType())));
        cs.endText();

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(60, 718);
        cs.showText(sanitizeGlyphs("患者ID: " + nvl(settle.getPatientId())
                + "    医保号: " + (settle.getInsuranceNo() == null || settle.getInsuranceNo().isBlank()
                ? "-" : settle.getInsuranceNo())));
        cs.endText();

        cs.setLineWidth(0.6f);
        cs.moveTo(50, 704);
        cs.lineTo(545, 704);
        cs.stroke();
        return 690;
    }

    /** 收费项目明细表：名称/类别/金额 */
    private float drawItemTable(PDPageContentStream cs, PDType0Font font, float y,
                                List<Map<String, Object>> detail) throws IOException {
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText(sanitizeGlyphs("收费项目名称"));
        cs.endText();
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(330, y);
        cs.showText(sanitizeGlyphs("医保类别"));
        cs.endText();
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(470, y);
        cs.showText(sanitizeGlyphs("金额(元)"));
        cs.endText();

        y -= 8;
        cs.setLineWidth(0.8f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();
        y -= 16;

        if (detail.isEmpty()) {
            cs.beginText();
            setFont(cs, font, 10);
            cs.newLineAtOffset(60, y);
            cs.showText(sanitizeGlyphs("（无逐项明细）"));
            cs.endText();
            y -= 18;
        }
        for (Map<String, Object> row : detail) {
            cs.beginText();
            setFont(cs, font, 10);
            cs.newLineAtOffset(60, y);
            cs.showText(sanitizeGlyphs(abbreviate(String.valueOf(row.getOrDefault("itemName", "-")), 22)));
            cs.endText();
            cs.beginText();
            setFont(cs, font, 10);
            cs.newLineAtOffset(330, y);
            cs.showText(sanitizeGlyphs(catalogClassText(str(row.get("catalogClass")))));
            cs.endText();
            cs.beginText();
            setFont(cs, font, 10);
            cs.newLineAtOffset(470, y);
            cs.showText(sanitizeGlyphs(String.valueOf(row.getOrDefault("amount", "0"))));
            cs.endText();

            y -= 6;
            cs.setLineWidth(0.3f);
            cs.moveTo(50, y);
            cs.lineTo(545, y);
            cs.stroke();
            y -= 12;
        }
        return y - 8;
    }

    /** 金额区：医保统筹支付/个人账户支付/现金支付/合计 */
    private float drawAmountZone(PDPageContentStream cs, PDType0Font font, float y, InsuranceSettle settle)
            throws IOException {
        cs.setLineWidth(0.8f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();
        y -= 22;

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(60, y);
        cs.showText(sanitizeGlyphs("医保统筹支付: " + money(settle.getInsurancePay()) + " 元"));
        cs.endText();
        y -= 20;

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(60, y);
        cs.showText(sanitizeGlyphs("个人账户支付: " + money(settle.getPersonalAccountPay()) + " 元"));
        cs.endText();
        y -= 20;

        cs.beginText();
        setFont(cs, font, 12);
        cs.newLineAtOffset(60, y);
        cs.showText(sanitizeGlyphs("现金支付: " + money(settle.getCashAmount()) + " 元"));
        cs.endText();
        y -= 24;

        cs.beginText();
        setFont(cs, font, 14);
        cs.newLineAtOffset(60, y);
        cs.showText(sanitizeGlyphs("合计: " + money(settle.getTotalAmount()) + " 元"
                + "（甲类 " + money(settle.getCatalogAAmount())
                + " / 乙类 " + money(settle.getCatalogBAmount())
                + " / 自费 " + money(settle.getSelfAmount()) + "）"));
        cs.endText();
        y -= 14;
        cs.setLineWidth(0.8f);
        cs.moveTo(50, y);
        cs.lineTo(545, y);
        cs.stroke();
        return y - 14;
    }

    /** 底部：结算时间 + 医保结算专用章（模拟） */
    private float drawFooter(PDPageContentStream cs, PDType0Font font, float y, InsuranceSettle settle)
            throws IOException {
        cs.beginText();
        setFont(cs, font, 11);
        cs.newLineAtOffset(60, y);
        cs.showText(sanitizeGlyphs("结算时间: " + (settle.getCreateTime() != null
                ? settle.getCreateTime().format(DATE_TIME) : "-")));
        cs.endText();

        cs.beginText();
        setFont(cs, font, 14);
        cs.newLineAtOffset(360, y - 40);
        cs.showText(sanitizeGlyphs("医保结算专用章（模拟）"));
        cs.endText();

        cs.beginText();
        setFont(cs, font, 9);
        cs.newLineAtOffset(50, 90);
        cs.showText(sanitizeGlyphs("本票据为毕业设计模拟数据，不作为实际报销凭证；结算规则：甲类统筹80%，乙类先行自付15%后剩余统筹80%，自费全额个人负担。"));
        cs.endText();
        return y;
    }

    // ==================== 工具方法 ====================

    private void setFont(PDPageContentStream cs, PDType0Font font, float size) throws IOException {
        if (font != null) {
            cs.setFont(font, size);
        } else {
            cs.setFont(PDType1Font.HELVETICA, size);
        }
    }

    /**
     * 字形兜底：中文字体缺少 Unicode 上标数字（U+2070~U+2079）与上标负号（U+207B）字形，
     * 直接渲染会抛 "No glyph" 导致整张票据 500。统一替换为 ASCII 兼容写法（⁹ → ^9）。
     */
    private static String sanitizeGlyphs(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String rep = null;
            if (c >= '\u2070' && c <= '\u2079') {
                rep = "^" + (c - '\u2070');
            } else if (c == '\u207B') {
                rep = "^-";
            }
            if (rep != null) {
                if (sb == null) {
                    sb = new StringBuilder(text.length() + 4).append(text, 0, i);
                }
                sb.append(rep);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? text : sb.toString();
    }

    /**
     * 字体链与既有 PdfService 一致（Windows 宋体 → Linux Noto CJK）；
     * .ttc 集合经 TrueTypeCollection 加载并保持打开（PDDocument.save() 时仍需读取字体数据流）。
     */
    private PDType0Font loadChineseFont(PDDocument document) {
        String[] candidates = {
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/msyh.ttc",
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
                    org.apache.fontbox.ttf.TrueTypeCollection ttc =
                            new org.apache.fontbox.ttf.TrueTypeCollection(f);
                    final org.apache.fontbox.ttf.TrueTypeFont[] holder =
                            new org.apache.fontbox.ttf.TrueTypeFont[1];
                    ttc.processAllFonts(ttf -> {
                        if (holder[0] == null) {
                            holder[0] = ttf;
                        }
                    });
                    return PDType0Font.load(document, holder[0], true);
                }
                return PDType0Font.load(document, f);
            } catch (Exception e) {
                log.warn("[医保票据] 字体加载失败: {}", path);
            }
        }
        log.warn("[医保票据] 未找到系统 CJK 字体，中文将无法嵌入 PDF");
        return null;
    }

    /** detail_json → 明细 Map 列表（解析失败返回空列表，票据仍可出具汇总金额） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(detailJson).stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .toList();
        } catch (Exception e) {
            log.warn("[医保票据] 明细 JSON 解析失败，票据仅展示汇总金额: {}", e.getMessage());
            return List.of();
        }
    }

    private static String catalogClassText(String catalogClass) {
        if (catalogClass == null) {
            return "未知";
        }
        return switch (catalogClass) {
            case "A" -> "甲类";
            case "B" -> "乙类";
            case "C" -> "自费";
            default -> catalogClass;
        };
    }

    private static String bizTypeText(String bizType) {
        if (bizType == null) {
            return "-";
        }
        return switch (bizType) {
            case "REGISTER" -> "门诊挂号";
            case "OUTPATIENT" -> "门诊费用";
            case "INPATIENT" -> "住院费用";
            default -> bizType;
        };
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nvl(Object o) {
        return o == null ? "-" : String.valueOf(o);
    }

    /** 中文按字符数截断（表格列宽有限，超长省略号收尾） */
    private static String abbreviate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() > maxChars ? text.substring(0, maxChars - 1) + "…" : text;
    }
}
