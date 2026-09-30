package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.medsupply.service.DrugGuidanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用药指导单接口（PDF 下载，返回方式与 clinic 医疗证明一致）
 */
@RestController
@RequestMapping("/api/medsupply/guidance")
@RequiredArgsConstructor
public class DrugGuidanceController {

    private final DrugGuidanceService drugGuidanceService;

    /** 按处方生成并下载用药指导单 PDF */
    @AuditLog(value = "打印用药指导单", operationType = "QUERY")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_GUIDANCE_PRINT)
    @GetMapping("/{prescriptionId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long prescriptionId) {
        byte[] bytes = drugGuidanceService.generatePdf(prescriptionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=drug-guidance-" + prescriptionId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
