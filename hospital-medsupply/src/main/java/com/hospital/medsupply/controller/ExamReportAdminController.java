package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 检查报告录入接口（检查技师 / 管理员，multipart/form-data）
 */
@RestController
@RequestMapping("/api/admin/exam")
@RequiredArgsConstructor
public class ExamReportAdminController {

    private final ExamService examService;

    /**
     * 录入检查报告
     *
     * @param applicationId 检查申请 ID（必填）
     * @param reportDesc    报告描述
     * @param reportResult  检查结果/诊断
     * @param status        DRAFT-草稿 / PENDING_AUDIT-待审核 / PUBLISHED-已发布（默认 PENDING_AUDIT）
     * @param file          报告附件（jpg/jpeg/png/pdf/doc/docx，≤10MB，可选）
     */
    @AuditLog(value = "录入检查报告", operationType = "INSERT")
    @RequiresPermission({PermissionConstant.MEDSUPPLY_EXAM_REPORT_CREATE})
    @PostMapping("/report")
    public Result<ExamReport> create(
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(value = "reportDesc", required = false) String reportDesc,
            @RequestParam(value = "reportResult", required = false) String reportResult,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        requireExamTechOrAdmin();
        return Result.ok(examService.createReport(
                applicationId, reportDesc, reportResult, file, status, UserContext.getUserId()));
    }

    /**
     * 报告审核（发布/驳回）
     *
     * @param id           报告 ID
     * @param status       目标状态（PUBLISHED / REJECTED）
     * @param auditComment 审核意见（可选）
     */
    @AuditLog(value = "检查报告审核", operationType = "UPDATE")
    @RequiresPermission({PermissionConstant.MEDSUPPLY_EXAM_REPORT_AUDIT})
    @PutMapping("/report/{id}/audit")
    public Result<ExamReport> audit(@PathVariable("id") Long id,
                                    @RequestParam("status") String status,
                                    @RequestParam(value = "auditComment", required = false) String auditComment) {
        requireExamTechOrAdmin();
        return Result.ok(examService.auditReport(id, status, auditComment, UserContext.getUserId()));
    }

    private void requireExamTechOrAdmin() {
        // 影像技师与检验技师均可录入/审核本人负责类型的报告，管理员与超管代为操作
        if (!UserContext.isExamTechOrAdmin() && !UserContext.isLabTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检查/检验技师或管理员可执行此操作");
        }
    }
}
