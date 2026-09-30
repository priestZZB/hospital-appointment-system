package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.Drug;
import com.hospital.medsupply.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/drug")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_QUERY)
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String keyword) {
        requireAdmin();
        return Result.ok(drugService.page(keyword, pageNo, pageSize));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_QUERY)
    @GetMapping("/{id}")
    public Result<Drug> getById(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(drugService.getById(id));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_CREATE)
    @PostMapping
    @AuditLog(value = "创建药品", operationType = "INSERT")
    public Result<Drug> create(@RequestBody Drug drug) {
        requireAdmin();
        return Result.ok(drugService.create(drug));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_UPDATE)
    @PutMapping("/{id}")
    @AuditLog(value = "更新药品", operationType = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @RequestBody Drug drug) {
        requireAdmin();
        drug.setId(id);
        drugService.update(drug);
        return Result.ok();
    }

    /** 药品三分类/管控级别/抗菌分级管理（V8 B1/B8/B10，body{drugType, controlLevel, antibioticLevel}） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_TYPE_MANAGE)
    @PutMapping("/{id}/type")
    @AuditLog(value = "药品分类管理", operationType = "UPDATE")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireAdmin();
        drugService.updateType(id, body.get("drugType"), body.get("controlLevel"), body.get("antibioticLevel"));
        return Result.ok();
    }

    private void requireAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }
}
