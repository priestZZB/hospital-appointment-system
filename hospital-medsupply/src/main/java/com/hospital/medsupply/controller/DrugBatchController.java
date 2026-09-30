package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DrugBatch;
import com.hospital.medsupply.service.DrugBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 药库批次管理接口（仅药师/管理员）
 * <p>
 * 采购入库落批次、效期预警、批次报损与批次分页。
 */
@RestController
@RequestMapping("/api/admin/drug/batch")
@RequiredArgsConstructor
public class DrugBatchController {

    private final DrugBatchService drugBatchService;

    /** 采购入库（插批次 + 联动总库存 + 麻精登记） */
    @AuditLog(value = "采购入库", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_BATCH_CREATE)
    @PostMapping("/inbound")
    public Result<DrugBatch> inbound(@RequestBody DrugBatch batch) {
        requirePharmacist();
        return Result.ok(drugBatchService.purchaseInbound(batch, UserContext.getUserId()));
    }

    /** 效期预警（30 天内到期批次） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_BATCH_LIST)
    @GetMapping("/expiring")
    public Result<List<DrugBatch>> expiring() {
        requirePharmacist();
        return Result.ok(drugBatchService.listExpiring());
    }

    /** 批次报损（清零 + 库存扣减 + 麻精 SCRAP 登记） */
    @AuditLog(value = "药品报损", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_SCRAP)
    @PostMapping("/{id}/scrap")
    public Result<DrugBatch> scrap(@PathVariable Long id,
                                   @RequestBody(required = false) Map<String, String> body) {
        requirePharmacist();
        return Result.ok(drugBatchService.scrap(id, body != null ? body.get("reason") : null,
                UserContext.getUserId()));
    }

    /** 批次分页 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DRUG_BATCH_LIST)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "drugId", required = false) Long drugId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        requirePharmacist();
        return Result.ok(drugBatchService.page(drugId, status, pageNo, pageSize));
    }

    private void requirePharmacist() {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可执行此操作");
        }
    }
}
