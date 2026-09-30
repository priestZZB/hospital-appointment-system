package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DrugRule;
import com.hospital.medsupply.service.DrugRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * CDSS 合理用药规则管理接口（仅药师/管理员）
 */
@RestController
@RequestMapping("/api/admin/drug/rule")
@RequiredArgsConstructor
public class DrugRuleController {

    private final DrugRuleService drugRuleService;

    /** 新增规则 */
    @AuditLog(value = "新增CDSS规则", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUGRULE_MANAGE)
    @PostMapping
    public Result<DrugRule> create(@RequestBody DrugRule rule) {
        requirePharmacist();
        return Result.ok(drugRuleService.create(rule));
    }

    /** 更新规则 */
    @AuditLog(value = "更新CDSS规则", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUGRULE_MANAGE)
    @PutMapping
    public Result<DrugRule> update(@RequestBody DrugRule rule) {
        requirePharmacist();
        return Result.ok(drugRuleService.update(rule));
    }

    /** 删除规则（软删 status=0） */
    @AuditLog(value = "删除CDSS规则", operationType = "DELETE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUGRULE_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requirePharmacist();
        drugRuleService.delete(id);
        return Result.ok();
    }

    /** 启用规则列表（可按类型/药品筛选） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUGRULE_MANAGE)
    @GetMapping("/list")
    public Result<List<DrugRule>> list(
            @RequestParam(value = "ruleType", required = false) String ruleType,
            @RequestParam(value = "drugId", required = false) Long drugId) {
        requirePharmacist();
        return Result.ok(drugRuleService.list(ruleType, drugId));
    }

    private void requirePharmacist() {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可执行此操作");
        }
    }
}
