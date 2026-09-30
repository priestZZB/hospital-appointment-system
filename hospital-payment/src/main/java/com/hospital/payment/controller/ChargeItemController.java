package com.hospital.payment.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.payment.entity.ChargeItem;
import com.hospital.payment.service.ChargeItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 统一收费项目目录接口（迭代11 H4，/api/admin/charge-item/**）。
 * <p>
 * price_status 状态机：ACTIVE --调价--> ADJUSTED（调价同事务写 charge_item_adjust_log），
 * 启用/停用切换 ACTIVE/DEPRECATED。
 */
@RestController
@RequestMapping("/api/admin/charge-item")
@RequiredArgsConstructor
public class ChargeItemController {

    private final ChargeItemService chargeItemService;

    /** 新增收费项目（item_code 唯一） */
    @AuditLog(value = "新增收费项目", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.PAYMENT_CHARGE_ITEM_MANAGE)
    @PostMapping
    public Result<ChargeItem> create(@RequestBody ChargeItem item) {
        return Result.ok(chargeItemService.create(item));
    }

    /** 编辑收费项目（改价自动留痕） */
    @AuditLog(value = "编辑收费项目", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_CHARGE_ITEM_MANAGE)
    @PutMapping("/{id}")
    public Result<ChargeItem> update(@PathVariable("id") Long id, @RequestBody ChargeItem item) {
        item.setId(id);
        return Result.ok(chargeItemService.update(id, item));
    }

    /** 调价：更新 unit_price + price_status=ADJUSTED + adjust_note，同事务写调价历史 */
    @AuditLog(value = "收费项目调价", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_CHARGE_ITEM_MANAGE)
    @PutMapping("/{id}/adjust-price")
    public Result<ChargeItem> adjustPrice(@PathVariable("id") Long id,
                                          @RequestParam("newPrice") BigDecimal newPrice,
                                          @RequestParam(value = "reason", required = false) String reason) {
        return Result.ok(chargeItemService.adjustPrice(id, newPrice, reason));
    }

    /** 启用/停用（status 仅允许 ACTIVE/DEPRECATED） */
    @AuditLog(value = "收费项目启用停用", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_CHARGE_ITEM_MANAGE)
    @PutMapping("/{id}/status")
    public Result<ChargeItem> updateStatus(@PathVariable("id") Long id,
                                           @RequestParam("status") String status) {
        return Result.ok(chargeItemService.updateStatus(id, status));
    }

    /** 分页查询（category 精确 + keyword 模糊匹配编码/名称） */
    @RequiresPermission(PermissionConstant.PAYMENT_CHARGE_ITEM_QUERY)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(chargeItemService.page(category, keyword, pageNo, pageSize));
    }
}
