package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.SurgeryApplyDTO;
import com.hospital.inpatient.service.SurgeryApplyService;
import com.hospital.inpatient.vo.SurgeryApplyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 手术申请接口（迭代6 E6 建单；排台/执行迭代10 完善）
 */
@RestController
@RequestMapping("/api/inpatient/surgery")
@RequiredArgsConstructor
public class SurgeryApplyController {

    private final SurgeryApplyService surgeryApplyService;

    @AuditLog(value = "开手术申请单", operationType = "INPATIENT_SURGERY_APPLY")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_APPLY)
    @PostMapping
    public Result<SurgeryApplyVO> apply(@Valid @RequestBody SurgeryApplyDTO dto) {
        return Result.ok(surgeryApplyService.apply(dto, UserContext.getUserId()));
    }

    @AuditLog(value = "手术排台", operationType = "INPATIENT_SURGERY_SCHEDULE")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_SCHEDULE)
    @PostMapping("/{id}/schedule")
    public Result<SurgeryApplyVO> schedule(@PathVariable("id") Long id,
                                           @RequestParam("scheduledTime")
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTime,
                                           @RequestParam(value = "operatingRoom", required = false) String operatingRoom) {
        return Result.ok(surgeryApplyService.schedule(id, scheduledTime, operatingRoom));
    }

    @AuditLog(value = "取消手术申请", operationType = "INPATIENT_SURGERY_CANCEL")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_SCHEDULE)
    @PostMapping("/{id}/cancel")
    public Result<SurgeryApplyVO> cancel(@PathVariable("id") Long id) {
        return Result.ok(surgeryApplyService.cancel(id));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_QUERY)
    @GetMapping
    public Result<List<SurgeryApplyVO>> list(
            @RequestParam(value = "admissionId", required = false) Long admissionId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(surgeryApplyService.list(admissionId, status));
    }
}
