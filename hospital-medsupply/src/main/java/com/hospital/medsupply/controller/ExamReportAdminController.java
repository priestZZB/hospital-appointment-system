package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 检查报告录入接口（管理员，multipart/form-data）
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
     * @param status        DRAFT-草稿 / PUBLISHED-已发布（默认 PUBLISHED）
     * @param file          报告附件（jpg/jpeg/png/pdf/doc/docx，≤10MB，可选）
     */
    @AuditLog(value = "录入检查报告", operationType = "INSERT")
    @PostMapping("/report")
    public Result<ExamReport> create(
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(value = "reportDesc", required = false) String reportDesc,
            @RequestParam(value = "reportResult", required = false) String reportResult,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        requireAdmin();
        return Result.ok(examService.createReport(
                applicationId, reportDesc, reportResult, file, status, UserContext.getUserId()));
    }

    private void requireAdmin() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }
}
