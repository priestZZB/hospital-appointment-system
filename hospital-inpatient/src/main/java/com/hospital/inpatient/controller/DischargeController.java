package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.DischargeDTO;
import com.hospital.inpatient.service.DischargeService;
import com.hospital.inpatient.vo.DischargeSummaryVO;
import com.hospital.inpatient.vo.MedicalRecordHomeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出院接口：出院小结 + 结算（多退少补）+ 病案首页
 */
@RestController
@RequestMapping("/api/inpatient/discharge")
@RequiredArgsConstructor
public class DischargeController {

    private final DischargeService dischargeService;

    @AuditLog(value = "办理出院", operationType = "INPATIENT_DISCHARGE")
    @RequiresPermission(PermissionConstant.INPATIENT_DISCHARGE)
    @PostMapping
    public Result<DischargeSummaryVO> discharge(@Valid @RequestBody DischargeDTO dto) {
        return Result.ok(dischargeService.discharge(dto, UserContext.getUserId()));
    }

    @AuditLog(value = "重新结算", operationType = "INPATIENT_RESETTLE")
    @RequiresPermission(PermissionConstant.INPATIENT_DISCHARGE)
    @PostMapping("/resettle")
    public Result<DischargeSummaryVO> resettle(@RequestParam("admissionId") Long admissionId) {
        return Result.ok(dischargeService.resettle(admissionId));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_ADMISSION_QUERY)
    @GetMapping("/summary")
    public Result<DischargeSummaryVO> summary(@RequestParam("admissionId") Long admissionId) {
        return Result.ok(dischargeService.summary(admissionId));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_HOME_QUERY)
    @GetMapping("/home")
    public Result<MedicalRecordHomeVO> home(@RequestParam("admissionId") Long admissionId) {
        return Result.ok(dischargeService.home(admissionId));
    }
}
