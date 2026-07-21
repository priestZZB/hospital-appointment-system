package com.hospital.clinic.controller;

import com.hospital.clinic.dto.AppointmentSubmitDTO;
import com.hospital.clinic.service.AppointmentService;
import com.hospital.clinic.vo.AppointmentVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预约挂号接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * 挂号下单
     * <p>
     * 患者需完成实名认证。使用分布式锁 + 乐观锁保证高并发下不超卖。
     */
    @AuditLog(value = "挂号下单", operationType = "INSERT")
    @PostMapping
    public Result<AppointmentVO> submit(@Valid @RequestBody AppointmentSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        log.info("[挂号] 患者挂号请求: userId={}, slotId={}", userId, dto.getSlotId());
        return Result.ok(appointmentService.submit(userId, dto));
    }

    /** 我的预约列表 */
    @GetMapping
    public Result<List<AppointmentVO>> list() {
        Long userId = UserContext.getUserId();
        return Result.ok(appointmentService.listByPatient(userId));
    }

    /** 预约详情 */
    @GetMapping("/{id}")
    public Result<AppointmentVO> getById(@PathVariable Long id) {
        return Result.ok(appointmentService.getById(id));
    }

    /** 取消预约 */
    @AuditLog(value = "取消预约", operationType = "UPDATE")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        appointmentService.cancel(id, reason != null ? reason : "患者取消预约");
        return Result.ok();
    }
}
