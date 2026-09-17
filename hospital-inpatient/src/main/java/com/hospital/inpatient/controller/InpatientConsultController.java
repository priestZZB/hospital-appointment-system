package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.ConsultCreateDTO;
import com.hospital.inpatient.dto.ConsultHandleDTO;
import com.hospital.inpatient.service.InpatientConsultService;
import com.hospital.inpatient.vo.ConsultVO;
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
 * 住院院内会诊接口（迭代6 E1）
 */
@RestController
@RequestMapping("/api/inpatient/consult")
@RequiredArgsConstructor
public class InpatientConsultController {

    private final InpatientConsultService consultService;

    @AuditLog(value = "发起住院会诊", operationType = "INPATIENT_CONSULT_CREATE")
    @RequiresPermission(PermissionConstant.INPATIENT_CONSULT_CREATE)
    @PostMapping
    public Result<ConsultVO> create(@Valid @RequestBody ConsultCreateDTO dto) {
        return Result.ok(consultService.create(dto, UserContext.getUserId()));
    }

    @AuditLog(value = "处理住院会诊", operationType = "INPATIENT_CONSULT_HANDLE")
    @RequiresPermission(PermissionConstant.INPATIENT_CONSULT_HANDLE)
    @PostMapping("/handle")
    public Result<ConsultVO> handle(@Valid @RequestBody ConsultHandleDTO dto) {
        return Result.ok(consultService.handle(dto, UserContext.getUserId()));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_CONSULT_QUERY)
    @GetMapping
    public Result<List<ConsultVO>> list(
            @RequestParam(value = "admissionId", required = false) Long admissionId,
            @RequestParam(value = "targetDeptId", required = false) Long targetDeptId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(consultService.list(admissionId, targetDeptId, status));
    }
}
