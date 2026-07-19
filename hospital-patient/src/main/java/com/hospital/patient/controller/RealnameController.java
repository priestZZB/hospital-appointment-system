package com.hospital.patient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.patient.dto.RealnameReviewDTO;
import com.hospital.patient.dto.RealnameSubmitDTO;
import com.hospital.patient.service.RealnameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实名认证接口
 */
@Slf4j
@RestController
@RequestMapping("/api/patient/realname")
@RequiredArgsConstructor
public class RealnameController {

    private final RealnameService realnameService;

    /** 提交实名认证 */
    @AuditLog(value = "提交实名认证", operationType = "INSERT")
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody RealnameSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        realnameService.submit(userId, dto);
        return Result.ok();
    }

    /** 管理员审核实名认证 */
    @AuditLog(value = "审核实名认证", operationType = "UPDATE")
    @PutMapping("/{patientId}/review")
    public Result<Void> review(@PathVariable Long patientId,
                               @Valid @RequestBody RealnameReviewDTO dto) {
        realnameService.review(patientId, dto);
        return Result.ok();
    }
}
