package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 检查执行接口（影像技师 / 管理员）
 * <p>
 * 影像技师仅处理非检验（item_type != LAB）的检查申请；
 * 检验申请由 {@link LabExecController} 处理。
 */
@Slf4j
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamExecController {

    private final ExamService examService;
    private final ExamApplicationMapper examApplicationMapper;
    private final PatientFeignClient patientFeignClient;

    /** 检查执行登记（状态 PENDING → EXECUTING） */
    @AuditLog(value = "检查执行登记", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAM_EXEC)
    @PutMapping("/application/{id}/execute")
    public Result<ExamApplication> execute(@PathVariable("id") Long applicationId) {
        requireExamTechOrAdmin();
        return Result.ok(examService.executeExam(applicationId, UserContext.getUserId()));
    }

    /** 影像科申请列表（按状态筛选，item_type != LAB） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAM_EXEC)
    @GetMapping("/application/list")
    public Result<List<ExamApplication>> list(
            @RequestParam("status") String status,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        requireExamTechOrAdmin();
        return Result.ok(examService.listImagingByStatus(status, offset, limit));
    }

    /** 患者未缴费检查申请列表（患者本人 / 收费员 / 管理员） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAM_UNPAID)
    @GetMapping("/application/unpaid")
    public Result<List<ExamApplication>> unpaid(@RequestParam("patientId") Long patientId) {
        requirePatientSelfOrCashierOrAdmin(patientId);
        return Result.ok(examApplicationMapper.selectUnpaidByPatient(patientId));
    }

    private void requireExamTechOrAdmin() {
        if (!UserContext.isExamTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检查技师或管理员可执行此操作");
        }
    }

    /** 未缴费列表权限：收费员/管理员可查任意，患者仅可查本人 */
    private void requirePatientSelfOrCashierOrAdmin(Long patientId) {
        if (UserContext.isCashierOrAdmin()) {
            return;
        }
        if (patientId != null && patientId.equals(resolvePatientId())) {
            return;
        }
        throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅患者本人、收费员或管理员可查询");
    }

    /** 通过 userId 解析 patientId（auth userId ≠ patient db id） */
    private Long resolvePatientId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.get("id") == null) {
                return null;
            }
            Object id = patientInfo.get("id");
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
            return Long.parseLong(id.toString());
        } catch (Exception e) {
            log.warn("[检查申请] 查询患者信息失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }
}
