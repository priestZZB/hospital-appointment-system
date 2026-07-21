package com.hospital.clinic.controller;

import com.hospital.clinic.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 预约内部接口（供 payment-service Feign 回调）
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/internal")
@RequiredArgsConstructor
public class AppointmentInternalController {

    private final AppointmentService appointmentService;

    /** payment-service 支付成功后确认号源锁定 */
    @PostMapping("/confirm-lock")
    public Map<String, Object> confirmLock(@RequestParam Long appointmentId) {
        appointmentService.confirmLock(appointmentId);
        return Map.of("success", true);
    }

    /** payment-service 超时关单后释放号源 */
    @PostMapping("/release-slot")
    public Map<String, Object> releaseSlot(@RequestParam Long appointmentId) {
        appointmentService.releaseSlotByPayment(appointmentId);
        return Map.of("success", true);
    }
}
