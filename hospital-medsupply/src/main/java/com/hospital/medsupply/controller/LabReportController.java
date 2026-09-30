package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.medsupply.service.LabReportPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式化验单打印接口（检验技师/管理员，迭代8 C4）
 * <p>
 * 按报告生成 PDF 化验单（二进制流返回，返回方式与用药指导单一致）。
 */
@RestController
@RequestMapping("/api/admin/lab")
@RequiredArgsConstructor
public class LabReportController {

    private final LabReportPdfService labReportPdfService;

    /** 按报告 ID 生成并下载化验单 PDF（异常结果行红字） */
    @AuditLog(value = "打印化验单", operationType = "QUERY")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_LABREPORT_PRINT)
    @GetMapping("/report/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long reportId) {
        requireLabTechOrAdmin();
        byte[] bytes = labReportPdfService.generatePdf(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=lab-report-" + reportId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private void requireLabTechOrAdmin() {
        if (!UserContext.isLabTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检验技师或管理员可执行此操作");
        }
    }
}
