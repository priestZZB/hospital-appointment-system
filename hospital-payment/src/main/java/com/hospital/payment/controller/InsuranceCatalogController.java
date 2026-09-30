package com.hospital.payment.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.payment.entity.InsuranceCatalog;
import com.hospital.payment.service.InsuranceCatalogService;
import com.hospital.payment.vo.InsuranceCatalogResolveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 医保目录映射接口（迭代11 H1，/api/admin/insurance-catalog/**）。
 * <p>
 * (itemType, refId) 唯一（uk_ins_cat）：POST 录入遇 UNIQUE 冲突改为更新（upsert）。
 */
@RestController
@RequestMapping("/api/admin/insurance-catalog")
@RequiredArgsConstructor
public class InsuranceCatalogController {

    private final InsuranceCatalogService catalogService;

    /** 录入映射（UNIQUE 冲突改更新） */
    @AuditLog(value = "医保目录录入", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_CATALOG_MANAGE)
    @PostMapping
    public Result<InsuranceCatalog> save(@RequestBody InsuranceCatalog catalog) {
        return Result.ok(catalogService.save(catalog));
    }

    /** 编辑映射（item_type/ref_id 唯一键不随编辑变化） */
    @AuditLog(value = "医保目录编辑", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_CATALOG_MANAGE)
    @PutMapping("/{id}")
    public Result<InsuranceCatalog> update(@PathVariable("id") Long id,
                                           @RequestBody InsuranceCatalog catalog) {
        return Result.ok(catalogService.update(id, catalog));
    }

    /** 分页查询（itemType/catalogClass 可选过滤） */
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_CATALOG_QUERY)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "itemType", required = false) String itemType,
            @RequestParam(value = "catalogClass", required = false) String catalogClass,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(catalogService.page(itemType, catalogClass, pageNo, pageSize));
    }

    /** 目录解析：未配置返回默认自费（catalogClass=C、reimburseRatio=0、configured=false） */
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_CATALOG_QUERY)
    @GetMapping("/resolve")
    public Result<InsuranceCatalogResolveVO> resolve(
            @RequestParam("itemType") String itemType,
            @RequestParam("refId") Long refId) {
        return Result.ok(catalogService.resolve(itemType, refId));
    }
}
