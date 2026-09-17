package com.hospital.clinic.controller;

import com.hospital.clinic.entity.ConsultationRequest;
import com.hospital.clinic.entity.ReferralOrder;
import com.hospital.clinic.service.ConsultationCollaborationService;
import com.hospital.clinic.vo.ConsultationRequestVO;
import com.hospital.clinic.vo.ReferralOrderVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
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

import java.util.List;

/**
 * 会诊 + 转诊协同接口（迭代6 功能补全）
 */
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class ConsultationCollaborationController {

    private final ConsultationCollaborationService collaborationService;

    // ==================== 会诊 ====================

    @AuditLog(value = "发起会诊", operationType = "CREATE_CONSULT_REQUEST")
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_REQUEST_CREATE)
    @PostMapping("/consult-request")
    public Result<ConsultationRequestVO> createConsultationRequest(@Valid @RequestBody ConsultationRequest request) {
        return Result.ok(collaborationService.createConsultationRequest(UserContext.getUserId(), request));
    }

    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_REQUEST_QUERY)
    @GetMapping("/consult-request/{id}")
    public Result<ConsultationRequestVO> getConsultationRequest(@PathVariable("id") Long id) {
        return Result.ok(collaborationService.getConsultationRequest(id));
    }

    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_REQUEST_QUERY)
    @GetMapping("/consult-request")
    public Result<List<ConsultationRequestVO>> listConsultationRequests(
            @RequestParam(value = "targetDeptId", required = false) Long targetDeptId,
            @RequestParam(value = "targetDoctorId", required = false) Long targetDoctorId,
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(collaborationService.listConsultationRequests(
                UserContext.getUserId(), targetDeptId, targetDoctorId, patientId, status));
    }

    @AuditLog(value = "处理会诊", operationType = "HANDLE_CONSULT_REQUEST")
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_REQUEST_HANDLE)
    @PutMapping("/consult-request/{id}/handle")
    public Result<Void> handleConsultationRequest(@PathVariable("id") Long id,
                                                  @RequestParam("action") String action,
                                                  @RequestParam(value = "opinion", required = false) String opinion) {
        collaborationService.handleConsultationRequest(UserContext.getUserId(), id, action, opinion);
        return Result.ok();
    }

    // ==================== 转诊 ====================

    @AuditLog(value = "创建转诊单", operationType = "CREATE_REFERRAL")
    @RequiresPermission(PermissionConstant.CLINIC_REFERRAL_CREATE)
    @PostMapping("/referral")
    public Result<ReferralOrderVO> createReferralOrder(@Valid @RequestBody ReferralOrder referral) {
        return Result.ok(collaborationService.createReferralOrder(UserContext.getUserId(), referral));
    }

    @RequiresPermission(PermissionConstant.CLINIC_REFERRAL_QUERY)
    @GetMapping("/referral/{id}")
    public Result<ReferralOrderVO> getReferralOrder(@PathVariable("id") Long id) {
        return Result.ok(collaborationService.getReferralOrder(id));
    }

    @RequiresPermission(PermissionConstant.CLINIC_REFERRAL_QUERY)
    @GetMapping("/referral")
    public Result<List<ReferralOrderVO>> listReferralOrders(
            @RequestParam(value = "toDeptId", required = false) Long toDeptId,
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(collaborationService.listReferralOrders(
                UserContext.getUserId(), toDeptId, patientId, status));
    }

    @AuditLog(value = "处理转诊单", operationType = "HANDLE_REFERRAL")
    @RequiresPermission(PermissionConstant.CLINIC_REFERRAL_HANDLE)
    @PutMapping("/referral/{id}/handle")
    public Result<Void> handleReferralOrder(@PathVariable("id") Long id,
                                            @RequestParam("action") String action) {
        collaborationService.handleReferralOrder(UserContext.getUserId(), id, action);
        return Result.ok();
    }
}
