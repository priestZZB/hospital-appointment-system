package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.VitalSignDTO;
import com.hospital.inpatient.service.VitalSignService;
import com.hospital.inpatient.vo.VitalSignVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生命体征接口（护士多时段录入）
 */
@RestController
@RequestMapping("/api/inpatient/vital")
@RequiredArgsConstructor
public class VitalSignController {

    private final VitalSignService vitalSignService;

    @AuditLog(value = "录入生命体征", operationType = "INPATIENT_VITAL_RECORD")
    @RequiresPermission(PermissionConstant.INPATIENT_VITAL_RECORD)
    @PostMapping
    public Result<VitalSignVO> record(@Valid @RequestBody VitalSignDTO dto) {
        return Result.ok(vitalSignService.record(dto, UserContext.getUserId()));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_ADMISSION_QUERY)
    @GetMapping
    public Result<List<VitalSignVO>> list(
            @RequestParam("admissionId") Long admissionId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(vitalSignService.list(admissionId, limit));
    }
}
