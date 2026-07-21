package com.hospital.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 支付服务 Feign 客户端
 * <p>
 * 供 clinic-service 调用创建订单、退款等接口。
 */
@FeignClient(name = "payment-service", path = "/api/payment")
public interface PaymentFeignClient {

    /**
     * 创建支付订单（内部调用，clinic-service 挂号下单时触发）
     *
     * @param dto 订单信息：appointmentId, patientId, amount, orderType
     * @return 订单信息 Map（含 orderId, orderNo, status）
     */
    @PostMapping("/internal/create")
    Map<String, Object> createOrder(@RequestBody Map<String, Object> dto);

    /**
     * 退款（内部调用，clinic-service 取消预约/停诊时触发）
     *
     * @param dto 退款信息：appointmentId, refundReason, refundType
     * @return 退款结果 Map
     */
    @PostMapping("/internal/refund")
    Map<String, Object> refund(@RequestBody Map<String, Object> dto);
}
