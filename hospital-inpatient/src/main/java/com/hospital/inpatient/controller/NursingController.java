package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.NursingRecordDTO;
import com.hospital.inpatient.service.NursingRecordService;
import com.hospital.inpatient.vo.NursingRecordVO;
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
 * 护理病历接口（迭代6 E2）
 */
@RestController
@RequestMapping("/api/inpatient/nursing")
@RequiredArgsConstructor
public class NursingController {

    private final NursingRecordService nursingRecordService;

    @AuditLog(value = "录入护理病历", operationType = "INPATIENT_NURSING_RECORD")
    @RequiresPermission(PermissionConstant.INPATIENT_NURSING_RECORD)
    @PostMapping
    public Result<NursingRecordVO> record(@Valid @RequestBody NursingRecordDTO dto) {
        return Result.ok(nursingRecordService.record(dto, UserContext.getUserId()));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_NURSING_QUERY)
    @GetMapping
    public Result<List<NursingRecordVO>> list(
            @RequestParam("admissionId") Long admissionId,
            @RequestParam(value = "recordType", required = false) String recordType,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(nursingRecordService.list(admissionId, recordType, limit));
    }
}
