package com.hospital.clinic.controller;

import com.hospital.clinic.entity.MedicalCertificate;
import com.hospital.clinic.service.MedicalCertificateService;
import com.hospital.clinic.vo.MedicalCertificateVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 医疗证明接口（迭代6 功能补全）
 */
@RestController
@RequestMapping("/api/clinic/certificate")
@RequiredArgsConstructor
public class MedicalCertificateController {

    private final MedicalCertificateService certificateService;

    @AuditLog(value = "开具医疗证明", operationType = "CREATE_CERTIFICATE")
    @RequiresPermission(PermissionConstant.CLINIC_CERTIFICATE_CREATE)
    @PostMapping
    public Result<MedicalCertificateVO> createCertificate(@Valid @RequestBody MedicalCertificate cert) {
        return Result.ok(certificateService.createCertificate(UserContext.getUserId(), cert));
    }

    @RequiresPermission(PermissionConstant.CLINIC_CERTIFICATE_QUERY)
    @GetMapping("/{id}")
    public Result<MedicalCertificateVO> getCertificate(@PathVariable("id") Long id) {
        return Result.ok(certificateService.getCertificate(id, UserContext.getUserId()));
    }

    @RequiresPermission(PermissionConstant.CLINIC_CERTIFICATE_QUERY)
    @GetMapping
    public Result<List<MedicalCertificateVO>> listCertificates(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "certType", required = false) String certType) {
        return Result.ok(certificateService.listCertificates(UserContext.getUserId(), patientId, certType));
    }

    @RequiresPermission(PermissionConstant.CLINIC_CERTIFICATE_DOWNLOAD)
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable("id") Long id) {
        byte[] bytes = certificateService.generatePdf(id, UserContext.getUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=certificate-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @AuditLog(value = "作废医疗证明", operationType = "CANCEL_CERTIFICATE")
    @RequiresPermission(PermissionConstant.CLINIC_CERTIFICATE_CREATE)
    @PutMapping("/{id}/cancel")
    public Result<Void> cancelCertificate(@PathVariable("id") Long id) {
        certificateService.cancelCertificate(UserContext.getUserId(), id);
        return Result.ok();
    }
}
