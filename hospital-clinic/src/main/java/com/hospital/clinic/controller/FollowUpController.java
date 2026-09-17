package com.hospital.clinic.controller;

import com.hospital.clinic.entity.FollowUpPlan;
import com.hospital.clinic.service.FollowUpService;
import com.hospital.clinic.vo.FollowUpPlanVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 随访接口（迭代6 功能补全）
 */
@RestController
@RequestMapping("/api/clinic/follow-up")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpService followUpService;

    @AuditLog(value = "创建随访计划", operationType = "CREATE_FOLLOW_UP")
    @RequiresPermission(PermissionConstant.CLINIC_FOLLOW_UP_CREATE)
    @PostMapping
    public Result<FollowUpPlanVO> createPlan(@Valid @RequestBody FollowUpPlan plan) {
        return Result.ok(followUpService.createPlan(UserContext.getUserId(), plan));
    }

    @RequiresPermission(PermissionConstant.CLINIC_FOLLOW_UP_QUERY)
    @GetMapping("/{id}")
    public Result<FollowUpPlanVO> getPlan(@PathVariable("id") Long id) {
        return Result.ok(followUpService.getPlan(id));
    }

    @RequiresPermission(PermissionConstant.CLINIC_FOLLOW_UP_QUERY)
    @GetMapping
    public Result<List<FollowUpPlanVO>> listPlans(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(followUpService.listPlans(UserContext.getUserId(), patientId, status));
    }

    @AuditLog(value = "填写随访记录", operationType = "ADD_FOLLOW_UP_RECORD")
    @RequiresPermission(PermissionConstant.CLINIC_FOLLOW_UP_RECORD)
    @PostMapping("/{id}/record")
    public Result<Void> addRecord(@PathVariable("id") Long id,
                                  @RequestParam(value = "content", required = false) String content,
                                  @RequestParam(value = "nextFollowDate", required = false) LocalDate nextFollowDate) {
        followUpService.addRecord(UserContext.getUserId(), id, content, nextFollowDate);
        return Result.ok();
    }

    @AuditLog(value = "取消随访计划", operationType = "CANCEL_FOLLOW_UP")
    @RequiresPermission(PermissionConstant.CLINIC_FOLLOW_UP_RECORD)
    @PutMapping("/{id}/cancel")
    public Result<Void> cancelPlan(@PathVariable("id") Long id) {
        followUpService.cancelPlan(UserContext.getUserId(), id);
        return Result.ok();
    }
}
