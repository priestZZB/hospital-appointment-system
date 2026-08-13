package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DrugDispense;
import com.hospital.medsupply.service.DispenseService;
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
 * 处方审核 / 发药管理接口（仅管理员）
 */
@RestController
@RequestMapping("/api/admin/drug/dispense")
@RequiredArgsConstructor
public class DispenseController {

    private final DispenseService dispenseService;

    /** 处方审核（APPROVE 通过 / REJECT 驳回） */
    @AuditLog(value = "处方审核", operationType = "UPDATE")
    @PutMapping("/{prescriptionId}/review")
    public Result<DrugDispense> review(@PathVariable Long prescriptionId,
                                       @RequestBody Map<String, String> body) {
        requireAdmin();
        return Result.ok(dispenseService.review(prescriptionId, body.get("action"),
                body.get("reviewComment"), UserContext.getUserId()));
    }

    /** 发药确认（乐观锁扣减库存） */
    @AuditLog(value = "发药确认", operationType = "UPDATE")
    @PostMapping("/{prescriptionId}")
    public Result<DrugDispense> dispense(@PathVariable Long prescriptionId) {
        requireAdmin();
        return Result.ok(dispenseService.dispense(prescriptionId, UserContext.getUserId()));
    }

    /** 发药记录分页（按状态筛选） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        requireAdmin();
        return Result.ok(dispenseService.page(status, pageNo, pageSize));
    }

    private void requireAdmin() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }
}
