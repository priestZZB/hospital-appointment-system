package com.hospital.clinic.controller;

import com.hospital.clinic.dto.DepartmentSaveDTO;
import com.hospital.clinic.service.DepartmentService;
import com.hospital.clinic.vo.DepartmentVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 科室管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /** 科室列表 */
    @GetMapping
    public Result<List<DepartmentVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(departmentService.list(keyword));
    }

    /** 科室详情 */
    @GetMapping("/{id}")
    public Result<DepartmentVO> getById(@PathVariable Long id) {
        return Result.ok(departmentService.getById(id));
    }

    /** 新增科室 */
    @AuditLog(value = "新增科室", operationType = "INSERT")
    @PostMapping
    public Result<DepartmentVO> create(@Valid @RequestBody DepartmentSaveDTO dto) {
        return Result.ok(departmentService.create(dto));
    }

    /** 编辑科室 */
    @AuditLog(value = "编辑科室", operationType = "UPDATE")
    @PutMapping("/{id}")
    public Result<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentSaveDTO dto) {
        return Result.ok(departmentService.update(id, dto));
    }

    /** 更新科室状态 */
    @AuditLog(value = "更新科室状态", operationType = "UPDATE")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        departmentService.updateStatus(id, status);
        return Result.ok();
    }
}
