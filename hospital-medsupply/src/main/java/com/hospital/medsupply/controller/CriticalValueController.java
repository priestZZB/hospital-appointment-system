package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.CriticalValue;
import com.hospital.medsupply.service.CriticalValueService;
import com.hospital.medsupply.vo.CriticalValueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 危急值接口（迭代6 功能补全）
 */
@RestController
@RequestMapping("/api/medsupply/critical")
@RequiredArgsConstructor
public class CriticalValueController {

    private final CriticalValueService criticalValueService;

    @AuditLog(value = "危急值上报", operationType = "REPORT_CRITICAL")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_CRITICAL_REPORT)
    @PostMapping
    public Result<CriticalValueVO> report(@RequestBody CriticalValue value) {
        requireReporter();
        return Result.ok(criticalValueService.report(UserContext.getUserId(), value));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_CRITICAL_QUERY)
    @GetMapping("/{id}")
    public Result<CriticalValueVO> getById(@PathVariable("id") Long id) {
        return Result.ok(criticalValueService.getById(id));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_CRITICAL_QUERY)
    @GetMapping
    public Result<List<CriticalValueVO>> list(@RequestParam(value = "status", required = false) String status,
                                              @RequestParam(value = "patientId", required = false) Long patientId) {
        return Result.ok(criticalValueService.list(status, patientId));
    }

    @AuditLog(value = "危急值复核", operationType = "CONFIRM_CRITICAL")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_CRITICAL_CONFIRM)
    @PutMapping("/{id}/confirm")
    public Result<CriticalValueVO> confirm(@PathVariable("id") Long id,
                                           @RequestParam("action") String action,
                                           @RequestParam(value = "comment", required = false) String comment) {
        return Result.ok(criticalValueService.confirm(UserContext.getUserId(), id, action, comment));
    }

    private void requireReporter() {
        if (!UserContext.isExamTechOrAdmin() && !UserContext.isLabTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检查/检验技师或管理员可上报危急值");
        }
    }
}
