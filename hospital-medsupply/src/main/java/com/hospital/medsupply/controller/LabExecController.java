package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检验执行接口（检验技师 / 管理员）
 * <p>
 * 检验技师仅处理检验（item_type = LAB）的申请；影像申请由 {@link ExamExecController} 处理。
 */
@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabExecController {

    private final ExamService examService;

    /** 检验执行登记（状态 PENDING → EXECUTING） */
    @AuditLog(value = "检验执行登记", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_LAB_EXEC)
    @PutMapping("/application/{id}/execute")
    public Result<ExamApplication> execute(@PathVariable("id") Long applicationId) {
        requireLabTechOrAdmin();
        return Result.ok(examService.executeExam(applicationId, UserContext.getUserId()));
    }

    /** 检验科申请列表（按状态筛选，item_type = LAB） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_LAB_EXEC)
    @GetMapping("/application/list")
    public Result<List<ExamApplication>> list(
            @RequestParam("status") String status,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        requireLabTechOrAdmin();
        return Result.ok(examService.listLabByStatus(status, offset, limit));
    }

    private void requireLabTechOrAdmin() {
        if (!UserContext.isLabTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检验技师或管理员可执行此操作");
        }
    }
}
