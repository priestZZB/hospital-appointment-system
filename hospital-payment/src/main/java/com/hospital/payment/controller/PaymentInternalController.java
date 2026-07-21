package com.hospital.payment.controller;

import com.hospital.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付内部接口（供 clinic-service Feign 调用）
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/internal")
@RequiredArgsConstructor
public class PaymentInternalController {

    private final PaymentService paymentService;

    /** clinic-service 挂号下单后创建支付订单 */
    @PostMapping("/create")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> dto) {
        log.info("[内部] 创建支付订单: appointmentId={}", dto.get("appointmentId"));
        return paymentService.createOrder(dto);
    }

    /** clinic-service 取消预约后触发退款 */
    @PostMapping("/refund")
    public Map<String, Object> refund(@RequestBody Map<String, Object> dto) {
        log.info("[内部] 退款请求: appointmentId={}", dto.get("appointmentId"));
        paymentService.refund(dto);
        return Map.of("success", true);
    }
}
