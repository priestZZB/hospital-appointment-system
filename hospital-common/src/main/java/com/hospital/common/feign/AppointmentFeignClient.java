package com.hospital.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 预约服务 Feign 客户端
 * <p>
 * 供 payment-service 回调使用（支付确认锁定号源、超时释放号源）。
 */
@FeignClient(name = "clinic-service", path = "/api/clinic")
public interface AppointmentFeignClient {

    /**
     * 确认号源锁定（支付成功后回调）
     *
     * @param appointmentId 预约 ID
     * @return 结果 Map
     */
    @PostMapping("/internal/confirm-lock")
    Map<String, Object> confirmLock(@RequestParam("appointmentId") Long appointmentId);

    /**
     * 释放号源（超时关单/退款后回调）
     *
     * @param appointmentId 预约 ID
     * @return 结果 Map
     */
    @PostMapping("/internal/release-slot")
    Map<String, Object> releaseSlot(@RequestParam("appointmentId") Long appointmentId);
}
