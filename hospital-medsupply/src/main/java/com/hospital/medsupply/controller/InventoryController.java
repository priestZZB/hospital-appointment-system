package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DrugInventory;
import com.hospital.medsupply.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/drug/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Boolean lowStock) {
        requireAdmin();
        return Result.ok(inventoryService.page(keyword, lowStock, pageNo, pageSize));
    }

    @PostMapping("/inbound")
    @AuditLog(value = "药品入库", operationType = "INSERT")
    public Result<DrugInventory> inbound(@RequestBody Map<String, Object> body) {
        requireAdmin();
        Long drugId = toLong(body.get("drugId"));
        int quantity = ((Number) body.get("quantity")).intValue();
        String remark = (String) body.getOrDefault("remark", "");
        return Result.ok(inventoryService.inbound(drugId, quantity, remark, UserContext.getUserId()));
    }

    @PostMapping("/outbound")
    @AuditLog(value = "药品出库", operationType = "UPDATE")
    public Result<DrugInventory> outbound(@RequestBody Map<String, Object> body) {
        requireAdmin();
        Long drugId = toLong(body.get("drugId"));
        int quantity = ((Number) body.get("quantity")).intValue();
        String remark = (String) body.getOrDefault("remark", "");
        return Result.ok(inventoryService.outbound(drugId, quantity, remark, UserContext.getUserId()));
    }

    @PostMapping("/adjust")
    @AuditLog(value = "库存盘点", operationType = "UPDATE")
    public Result<DrugInventory> adjust(@RequestBody Map<String, Object> body) {
        requireAdmin();
        Long drugId = toLong(body.get("drugId"));
        int quantity = ((Number) body.get("quantity")).intValue();
        String remark = (String) body.getOrDefault("remark", "");
        return Result.ok(inventoryService.adjust(drugId, quantity, remark, UserContext.getUserId()));
    }

    private void requireAdmin() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }

    private Long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }
}
