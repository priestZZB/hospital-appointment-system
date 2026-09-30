package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DrugReturn;
import com.hospital.medsupply.service.DrugReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 退药管理接口（仅药师/管理员）
 * <p>
 * 发药后退药冲账：库存回冲 + 批次回冲（RETURN 记录）+ 金额冲账。
 */
@RestController
@RequestMapping("/api/admin/drug/return")
@RequiredArgsConstructor
public class DrugReturnController {

    private final DrugReturnService drugReturnService;

    /** 退药冲账 */
    @AuditLog(value = "退药冲账", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_RETURN_CREATE)
    @PostMapping
    public Result<DrugReturn> create(@RequestBody DrugReturn drugReturn) {
        requirePharmacist();
        return Result.ok(drugReturnService.create(drugReturn, UserContext.getUserId()));
    }

    /** 退药单分页 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_BATCH_LIST)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        requirePharmacist();
        return Result.ok(drugReturnService.page(patientId, status, pageNo, pageSize));
    }

    private void requirePharmacist() {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可执行此操作");
        }
    }
}
