package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DoctorAntibioticAuth;
import com.hospital.medsupply.service.AntibioticAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 抗菌药物分级授权接口（仅管理员）
 */
@RestController
@RequestMapping("/api/admin/drug/antibiotic-auth")
@RequiredArgsConstructor
public class AntibioticAuthController {

    private final AntibioticAuthService antibioticAuthService;

    /** 授权（body{doctorId, maxLevel}，doctor_id 唯一，重复授权即更新） */
    @AuditLog(value = "抗菌药物授权", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_ANTIBIOTIC_AUTH)
    @PostMapping("/grant")
    public Result<DoctorAntibioticAuth> grant(@RequestBody Map<String, Object> body) {
        requireAdmin();
        Long doctorId = toLong(body.get("doctorId"));
        String maxLevel = body.get("maxLevel") != null ? body.get("maxLevel").toString() : null;
        return Result.ok(antibioticAuthService.grant(doctorId, maxLevel, UserContext.getUserId()));
    }

    /** 撤销授权 */
    @AuditLog(value = "撤销抗菌授权", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_ANTIBIOTIC_AUTH)
    @DeleteMapping("/{doctorId}")
    public Result<Void> revoke(@PathVariable Long doctorId) {
        requireAdmin();
        antibioticAuthService.revoke(doctorId);
        return Result.ok();
    }

    /** 授权列表 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_ANTIBIOTIC_AUTH)
    @GetMapping("/list")
    public Result<List<DoctorAntibioticAuth>> list() {
        requireAdmin();
        return Result.ok(antibioticAuthService.list());
    }

    private void requireAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
