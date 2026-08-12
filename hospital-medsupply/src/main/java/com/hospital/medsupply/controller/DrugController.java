package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
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

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String keyword) {
        requireAdmin();
        return Result.ok(drugService.page(keyword, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Drug> getById(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(drugService.getById(id));
    }

    @PostMapping
    @AuditLog(value = "创建药品", operationType = "INSERT")
    public Result<Drug> create(@RequestBody Drug drug) {
        requireAdmin();
        return Result.ok(drugService.create(drug));
    }

    @PutMapping("/{id}")
    @AuditLog(value = "更新药品", operationType = "UPDATE")
    public Result<Void> update(@PathVariable Long id, @RequestBody Drug drug) {
        requireAdmin();
        drug.setId(id);
        drugService.update(drug);
        return Result.ok();
    }

    private void requireAdmin() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }
}
