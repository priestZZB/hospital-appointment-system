package com.hospital.payment.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.payment.service.PaymentService;
import com.hospital.payment.service.PdfService;
import com.hospital.payment.vo.PaymentOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 支付接口
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PdfService pdfService;

    /** 模拟支付 */
    @AuditLog(value = "模拟支付", operationType = "UPDATE")
    @PostMapping("/pay")
    public Result<PaymentOrderVO> pay(@RequestParam("orderId") Long orderId) {
        return Result.ok(paymentService.processPayment(orderId, UserContext.getUserId()));
    }

    /** 查询订单状态 */
    @GetMapping("/status/{orderId}")
    public Result<PaymentOrderVO> getStatus(@PathVariable Long orderId) {
        return Result.ok(paymentService.getStatus(orderId, UserContext.getUserId()));
    }

    /** 下载挂号凭证 PDF */
    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long orderId) {
        paymentService.checkOrderOwner(orderId, UserContext.getUserId());
        byte[] pdf = pdfService.generateReceipt(orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + orderId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** 扫表兜底（管理员手动触发） */
    @AuditLog(value = "扫表关单", operationType = "UPDATE")
    @PostMapping("/scan-timeout")
    public Result<Void> scanTimeout() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可触发扫表关单");
        }
        paymentService.scanTimeoutOrders();
        return Result.ok();
    }
}
