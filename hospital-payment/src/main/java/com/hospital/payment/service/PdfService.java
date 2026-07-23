package com.hospital.payment.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.payment.entity.PaymentOrder;
import com.hospital.payment.mapper.PaymentOrderMapper;
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

/**
 * PDF 挂号凭证生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final PaymentOrderMapper orderMapper;

    /**
     * 生成挂号凭证 PDF
     *
     * @param orderId 订单ID
     * @return PDF 字节数组
     */
    public byte[] generateReceipt(Long orderId) {
        PaymentOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // 加载支持中文的字体
            PDType0Font fontChinese;
            try {
                fontChinese = PDType0Font.load(document, new File("C:/Windows/Fonts/simsun.ttc"));
            } catch (Exception e) {
                try {
                    fontChinese = PDType0Font.load(document, new File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
                } catch (Exception e2) {
                    log.warn("[PDF] 未找到系统 CJK 字体，使用默认字体（中文可能无法正确显示）", e2);
                    fontChinese = null;
                }
            }

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // 标题
                cs.beginText();
                if (fontChinese != null) {
                    cs.setFont(fontChinese, 20);
                } else {
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 20);
                }
                cs.newLineAtOffset(200, 750);
                cs.showText("Hospital Appointment Receipt");
                cs.endText();

                // 分隔线
                cs.moveTo(50, 730);
                cs.lineTo(550, 730);
                cs.stroke();

                // 订单信息
                float y = 700;
                float lineHeight = 25;

                cs.beginText();
                if (fontChinese != null) {
                    cs.setFont(fontChinese, 12);
                } else {
                    cs.setFont(PDType1Font.HELVETICA, 12);
                }

                cs.newLineAtOffset(50, y);
                cs.showText("Order No: " + order.getOrderNo());
                y -= lineHeight;

                cs.newLineAtOffset(0, -lineHeight);
                cs.showText("Status: " + order.getStatus());
                y -= lineHeight;

                cs.newLineAtOffset(0, -lineHeight);
                cs.showText("Amount: " + order.getAmount() + " CNY");
                y -= lineHeight;

                if (order.getPayTime() != null) {
                    cs.newLineAtOffset(0, -lineHeight);
                    cs.showText("Pay Time: " + order.getPayTime());
                    y -= lineHeight;
                }

                cs.newLineAtOffset(0, -lineHeight);
                cs.showText("Pay Method: " + (order.getPayMethod() != null ? order.getPayMethod() : "N/A"));
                y -= lineHeight;

                cs.newLineAtOffset(0, -lineHeight);
                cs.showText("Created: " + order.getCreateTime());

                cs.endText();

                // 底部说明
                cs.beginText();
                if (fontChinese != null) {
                    cs.setFont(fontChinese, 10);
                } else {
                    cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 10);
                }
                cs.newLineAtOffset(50, 100);
                cs.showText("This is a simulated receipt for demonstration purposes.");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("[PDF] 凭证生成成功: orderId={}, size={}bytes", orderId, baos.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("[PDF] 凭证生成失败: orderId={}", orderId, e);
            throw new RuntimeException("PDF 生成失败", e);
        }
    }
}
