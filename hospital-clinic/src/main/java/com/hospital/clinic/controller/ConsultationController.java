package com.hospital.clinic.controller;

import com.hospital.clinic.dto.ExamRequestDTO;
import com.hospital.clinic.dto.MedicalRecordSaveDTO;
import com.hospital.clinic.dto.PrescriptionCreateDTO;
import com.hospital.clinic.service.ConsultationService;
import com.hospital.clinic.vo.MedicalRecordVO;
import com.hospital.clinic.vo.PrescriptionVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 门诊诊疗接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    /** 开始接诊 */
    @AuditLog(value = "开始接诊", operationType = "START_CONSULTATION")
    @PostMapping("/consultation/start")
    public Result<MedicalRecordVO> startConsultation(@RequestParam("appointmentId") Long appointmentId) {
        Long userId = UserContext.getUserId();
        return Result.ok(consultationService.startConsultation(appointmentId, userId));
    }

    /** 保存病历（草稿/提交） */
    @AuditLog(value = "保存病历", operationType = "SAVE_MEDICAL_RECORD")
    @PutMapping("/consultation/{recordId}")
    public Result<MedicalRecordVO> saveMedicalRecord(@PathVariable("recordId") Long recordId,
                                                      @Valid @RequestBody MedicalRecordSaveDTO dto) {
        return Result.ok(consultationService.saveMedicalRecord(recordId, dto));
    }

    /** 处方开具 */
    @AuditLog(value = "处方开具", operationType = "CREATE_PRESCRIPTION")
    @PostMapping("/prescription")
    public Result<PrescriptionVO> createPrescription(@Valid @RequestBody PrescriptionCreateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(consultationService.createPrescription(userId, dto));
    }

    /** 检查/检验申请 */
    @AuditLog(value = "检查检验申请", operationType = "REQUEST_EXAM")
    @PostMapping("/consultation/exam")
    public Result<Void> requestExam(@Valid @RequestBody ExamRequestDTO dto) {
        Long userId = UserContext.getUserId();
        consultationService.requestExam(userId, dto);
        return Result.ok();
    }

    /** 结束就诊 */
    @AuditLog(value = "结束就诊", operationType = "FINISH_CONSULTATION")
    @PutMapping("/consultation/{recordId}/finish")
    public Result<Void> finishConsultation(@PathVariable("recordId") Long recordId) {
        consultationService.finishConsultation(recordId);
        return Result.ok();
    }

    /** 病历详情 */
    @GetMapping("/consultation/{recordId}")
    public Result<MedicalRecordVO> getMedicalRecord(@PathVariable("recordId") Long recordId) {
        return Result.ok(consultationService.getMedicalRecord(recordId));
    }

    /** 患者病历列表 */
    @GetMapping("/consultation/patient/{patientId}")
    public Result<List<MedicalRecordVO>> listByPatient(@PathVariable("patientId") Long patientId,
                                                        @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        long offset = (long) (Math.max(pageNo, 1) - 1) * Math.min(pageSize, 100);
        return Result.ok(consultationService.listByPatient(patientId, offset, pageSize));
    }
}
