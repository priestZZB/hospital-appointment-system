package com.hospital.clinic.controller;

import com.hospital.clinic.dto.DoctorSaveDTO;
import com.hospital.clinic.service.DoctorService;
import com.hospital.clinic.vo.DoctorVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
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
 * 医生管理接口
 */
@RestController
@RequestMapping("/api/clinic/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    /** 分页查询医生（科室/关键字筛选） */
    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return Result.ok(doctorService.page(departmentId, keyword, pageNo, pageSize));
    }

    /** 医生详情 */
    @GetMapping("/{id}")
    public Result<DoctorVO> getById(@PathVariable Long id) {
        return Result.ok(doctorService.getById(id));
    }

    /** 新增医生（仅管理员） */
    @AuditLog(value = "新增医生", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.CLINIC_DOCTOR_CREATE)
    @PostMapping
    public Result<DoctorVO> create(@Valid @RequestBody DoctorSaveDTO dto) {
        checkAdmin();
        return Result.ok(doctorService.create(dto));
    }

    /** 编辑医生（仅管理员） */
    @AuditLog(value = "编辑医生", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_DOCTOR_UPDATE)
    @PutMapping("/{id}")
    public Result<DoctorVO> update(@PathVariable Long id, @Valid @RequestBody DoctorSaveDTO dto) {
        checkAdmin();
        return Result.ok(doctorService.update(id, dto));
    }

    /** 启用/停用医生（仅管理员） */
    @AuditLog(value = "医生状态变更", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_DOCTOR_STATUS)
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        checkAdmin();
        Integer status = body.get("status");
        if (status == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "status 不能为空");
        }
        doctorService.updateStatus(id, status);
        return Result.ok();
    }

    private void checkAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }
}
