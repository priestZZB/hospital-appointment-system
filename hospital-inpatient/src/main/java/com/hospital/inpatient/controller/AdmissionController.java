package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.AdmissionCreateDTO;
import com.hospital.inpatient.dto.BedAssignDTO;
import com.hospital.inpatient.dto.TransferDeptDTO;
import com.hospital.inpatient.service.AdmissionService;
import com.hospital.inpatient.vo.AdmissionVO;
import com.hospital.inpatient.vo.BedVO;
import com.hospital.inpatient.vo.InpatientOverviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 入院登记与床位接口（迭代6 住院模块）
 */
@RestController
@RequestMapping("/api/inpatient/admission")
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;

    @AuditLog(value = "入院登记", operationType = "INPATIENT_ADMIT")
    @RequiresPermission(PermissionConstant.INPATIENT_ADMISSION_CREATE)
    @PostMapping
    public Result<AdmissionVO> admit(@Valid @RequestBody AdmissionCreateDTO dto) {
        return Result.ok(admissionService.admit(dto, com.hospital.common.interceptor.UserContext.getUserId()));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_ADMISSION_QUERY)
    @GetMapping
    public Result<List<AdmissionVO>> list(
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return Result.ok(admissionService.list(departmentId, doctorId, status, pageNo, pageSize));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_ADMISSION_QUERY)
    @GetMapping("/{id}")
    public Result<AdmissionVO> detail(@PathVariable("id") Long id) {
        return Result.ok(admissionService.detail(id));
    }

    @AuditLog(value = "分床/转床", operationType = "INPATIENT_BED_ASSIGN")
    @RequiresPermission(PermissionConstant.INPATIENT_BED_ASSIGN)
    @PostMapping("/assign-bed")
    public Result<AdmissionVO> assignBed(@Valid @RequestBody BedAssignDTO dto) {
        return Result.ok(admissionService.assignBed(dto));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_BED_QUERY)
    @GetMapping("/beds")
    public Result<List<BedVO>> beds(@RequestParam(value = "departmentId", required = false) Long departmentId) {
        return Result.ok(admissionService.bedsOf(departmentId));
    }

    @AuditLog(value = "住院转科", operationType = "INPATIENT_TRANSFER")
    @RequiresPermission(PermissionConstant.INPATIENT_TRANSFER_DEPT)
    @PostMapping("/transfer-dept")
    public Result<AdmissionVO> transferDept(@Valid @RequestBody TransferDeptDTO dto) {
        return Result.ok(admissionService.transferDept(dto));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_OVERVIEW)
    @GetMapping("/overview")
    public Result<List<InpatientOverviewVO>> overview(
            @RequestParam(value = "departmentId", required = false) Long departmentId) {
        return Result.ok(admissionService.overview(departmentId));
    }
}
