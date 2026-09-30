package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DrugTransfer;
import com.hospital.medsupply.service.DrugTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 药品调拨接口（仅药师/管理员）
 * <p>
 * 药库 → 药房 → 科室流转留痕（简化模式：总库存不变、批次不拆分）。
 */
@RestController
@RequestMapping("/api/admin/drug/transfer")
@RequiredArgsConstructor
public class DrugTransferController {

    private final DrugTransferService drugTransferService;

    /** 创建调拨单 */
    @AuditLog(value = "药品调拨", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_TRANSFER_CREATE)
    @PostMapping
    public Result<DrugTransfer> create(@RequestBody DrugTransfer transfer) {
        requirePharmacist();
        return Result.ok(drugTransferService.create(transfer, UserContext.getUserId()));
    }

    /** 调拨单分页 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_BATCH_LIST)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "drugId", required = false) Long drugId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        requirePharmacist();
        return Result.ok(drugTransferService.page(drugId, status, pageNo, pageSize));
    }

    private void requirePharmacist() {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可执行此操作");
        }
    }
}
