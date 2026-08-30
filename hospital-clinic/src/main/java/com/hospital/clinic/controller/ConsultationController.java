package com.hospital.clinic.controller;

import com.hospital.clinic.dto.ExamRequestDTO;
import com.hospital.clinic.dto.MedicalRecordSaveDTO;
import com.hospital.clinic.dto.PrescriptionCreateDTO;
import com.hospital.clinic.service.AppointmentService;
import com.hospital.clinic.service.ConsultationService;
import com.hospital.clinic.vo.MedicalRecordVO;
import com.hospital.clinic.vo.PrescriptionVO;
import com.hospital.clinic.vo.QueuePatientVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
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
    private final AppointmentService appointmentService;

    /** 开始接诊 */
    @AuditLog(value = "开始接诊", operationType = "START_CONSULTATION")
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_START)
    @PostMapping("/consultation/start")
    public Result<MedicalRecordVO> startConsultation(@RequestParam("appointmentId") Long appointmentId) {
        Long userId = UserContext.getUserId();
        return Result.ok(consultationService.startConsultation(appointmentId, userId));
    }

    /** 保存病历（草稿/提交） */
    @AuditLog(value = "保存病历", operationType = "SAVE_MEDICAL_RECORD")
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_SAVE)
    @PutMapping("/consultation/{recordId}")
    public Result<MedicalRecordVO> saveMedicalRecord(@PathVariable("recordId") Long recordId,
                                                      @Valid @RequestBody MedicalRecordSaveDTO dto) {
        return Result.ok(consultationService.saveMedicalRecord(recordId, dto));
    }

    /** 处方开具 */
    @AuditLog(value = "处方开具", operationType = "CREATE_PRESCRIPTION")
    @RequiresPermission(PermissionConstant.CLINIC_PRESCRIPTION_CREATE)
    @PostMapping("/prescription")
    public Result<PrescriptionVO> createPrescription(@Valid @RequestBody PrescriptionCreateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(consultationService.createPrescription(userId, dto));
    }

    /** 检查/检验申请 */
    @AuditLog(value = "检查检验申请", operationType = "REQUEST_EXAM")
    @RequiresPermission(PermissionConstant.CLINIC_EXAM_REQUEST)
    @PostMapping("/consultation/exam")
    public Result<Void> requestExam(@Valid @RequestBody ExamRequestDTO dto) {
        Long userId = UserContext.getUserId();
        consultationService.requestExam(userId, dto);
        return Result.ok();
    }

    /** 结束就诊 */
    @AuditLog(value = "结束就诊", operationType = "FINISH_CONSULTATION")
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_FINISH)
    @PutMapping("/consultation/{recordId}/finish")
    public Result<Void> finishConsultation(@PathVariable("recordId") Long recordId) {
        consultationService.finishConsultation(recordId);
        return Result.ok();
    }

    /** 病历详情 */
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_QUERY)
    @GetMapping("/consultation/{recordId}")
    public Result<MedicalRecordVO> getMedicalRecord(@PathVariable("recordId") Long recordId) {
        return Result.ok(consultationService.getMedicalRecord(recordId, UserContext.getUserId()));
    }

    /** 患者病历列表 */
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_PATIENT)
    @GetMapping("/consultation/patient/{patientId}")
    public Result<List<MedicalRecordVO>> listByPatient(@PathVariable("patientId") Long patientId,
                                                        @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        long offset = (long) (Math.max(pageNo, 1) - 1) * Math.min(pageSize, 100);
        return Result.ok(consultationService.listByPatient(patientId, offset, pageSize, UserContext.getUserId()));
    }

    /** 医生工作台：今日待接诊/已叫号患者列表（医生/管理员） */
    @RequiresPermission(PermissionConstant.CLINIC_CONSULT_TODAY)
    @GetMapping("/consultation/today")
    public Result<List<QueuePatientVO>> todayQueue(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId) {
        checkDoctorOrAdmin();
        return Result.ok(appointmentService.todayQueue(departmentId, doctorId));
    }

    private void checkDoctorOrAdmin() {
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }
}
