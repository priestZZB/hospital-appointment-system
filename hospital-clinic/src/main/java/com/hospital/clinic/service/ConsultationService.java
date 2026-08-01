package com.hospital.clinic.service;

import cn.hutool.core.lang.UUID;
import com.hospital.clinic.dto.ExamRequestDTO;
import com.hospital.clinic.dto.MedicalRecordSaveDTO;
import com.hospital.clinic.dto.PrescriptionCreateDTO;
import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.MedicalRecord;
import com.hospital.clinic.entity.Prescription;
import com.hospital.clinic.entity.PrescriptionItem;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.MedicalRecordMapper;
import com.hospital.clinic.mapper.PrescriptionItemMapper;
import com.hospital.clinic.mapper.PrescriptionMapper;
import com.hospital.clinic.vo.MedicalRecordVO;
import com.hospital.clinic.vo.PrescriptionItemVO;
import com.hospital.clinic.vo.PrescriptionVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 门诊诊疗服务
 * <p>
 * 接诊开始、病历书写（草稿/提交）、处方开具、检查申请、结束就诊。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final AppointmentMapper appointmentMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final DoctorMapper doctorMapper;
    private final RestTemplate restTemplate;

    /**
     * 接诊开始
     * <p>
     * 创建病历草稿 → 更新 visit_status 为 IN_PROGRESS
     *
     * @param appointmentId 预约 ID
     * @param doctorId      医生 ID
     * @return 病历 VO（草稿状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public MedicalRecordVO startConsultation(Long appointmentId, Long userId) {
        // 权限校验 + userId→doctorId 映射
        com.hospital.clinic.entity.Doctor consultingDoctor = doctorMapper.selectByUserId(userId);
        if (consultingDoctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }

        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BusinessException(ErrorCodeEnum.APPOINTMENT_NOT_FOUND);
        }
        if (!Objects.equals(appointment.getDoctorId(), consultingDoctor.getId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "您不是该预约的看诊医生");
        }
        if (appointment.getVisitStatus() != null && appointment.getVisitStatus().equals("IN_PROGRESS")) {
            throw new BusinessException(ErrorCodeEnum.CONSULTATION_IN_PROGRESS);
        }

        Long doctorId = consultingDoctor.getId();

        // 创建病历草稿
        MedicalRecord record = new MedicalRecord();
        record.setAppointmentId(appointmentId);
        record.setPatientId(appointment.getPatientId());
        record.setDoctorId(doctorId);
        record.setDepartmentId(appointment.getDepartmentId());
        record.setStatus("DRAFT");
        record.setIsReturnVisit(appointment.getIsRevisit() != null ? appointment.getIsRevisit() : 0);
        medicalRecordMapper.insert(record);

        // 更新 visit_status
        appointmentMapper.updateVisitStatus(appointmentId, "IN_PROGRESS", null);
        log.info("[接诊] 开始接诊: appointmentId={}, recordId={}", appointmentId, record.getId());

        return toVO(record, Collections.emptyList());
    }

    /**
     * 病历保存（草稿 或 提交）
     *
     * @param recordId 病历 ID
     * @param dto      病历内容 + action(DRAFT/SUBMIT)
     * @return 病历 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public MedicalRecordVO saveMedicalRecord(Long recordId, MedicalRecordSaveDTO dto) {
        MedicalRecord record = medicalRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        if ("COMPLETED".equals(record.getStatus()) || "SUBMITTED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.RECORD_ALREADY_SUBMITTED);
        }

        // 填充字段
        if (dto.getChiefComplaint() != null) record.setChiefComplaint(dto.getChiefComplaint());
        if (dto.getPresentIllness() != null) record.setPresentIllness(dto.getPresentIllness());
        if (dto.getPastHistory() != null) record.setPastHistory(dto.getPastHistory());
        if (dto.getTemperature() != null) record.setTemperature(dto.getTemperature());
        if (dto.getPulse() != null) record.setPulse(dto.getPulse());
        if (dto.getRespiration() != null) record.setRespiration(dto.getRespiration());
        if (dto.getBloodPressure() != null) record.setBloodPressure(dto.getBloodPressure());
        if (dto.getDiagnosisCode() != null) record.setDiagnosisCode(dto.getDiagnosisCode());
        if (dto.getDiagnosisDesc() != null) record.setDiagnosisDesc(dto.getDiagnosisDesc());
        if (dto.getTreatmentOpinion() != null) record.setTreatmentOpinion(dto.getTreatmentOpinion());
        if (dto.getReferralDeptId() != null) record.setReferralDeptId(dto.getReferralDeptId());
        if (dto.getReferralReason() != null) record.setReferralReason(dto.getReferralReason());

        String action = dto.getAction();
        if ("SUBMIT".equals(action)) {
            if (record.getDiagnosisDesc() == null || record.getDiagnosisDesc().isBlank()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "提交病历时诊断描述不能为空");
            }
            record.setStatus("SUBMITTED");
        } else {
            record.setStatus("DRAFT");
        }

        medicalRecordMapper.update(record);
        log.info("[病历] 保存: recordId={}, action={}", recordId, action);

        // 查询关联处方
        List<Prescription> prescriptions = prescriptionMapper.selectByMedicalRecordId(recordId);
        List<PrescriptionVO> prescriptionVOs = prescriptions.stream()
                .map(p -> {
                    List<PrescriptionItem> items = prescriptionItemMapper.selectByPrescriptionId(p.getId());
                    return toPrescriptionVO(p, items);
                })
                .collect(Collectors.toList());

        return toVO(record, prescriptionVOs);
    }

    /**
     * 处方开具
     * <p>
     * 创建 prescription + prescription_item
     *
     * @param doctorId 医生 ID
     * @param dto      处方信息
     * @return 处方 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PrescriptionVO createPrescription(Long userId, PrescriptionCreateDTO dto) {
        com.hospital.clinic.entity.Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }

        MedicalRecord record = medicalRecordMapper.selectById(dto.getMedicalRecordId());
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        if (!Objects.equals(record.getDoctorId(), doctor.getId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权为此病历开具处方");
        }

        // 创建处方主表
        Prescription prescription = new Prescription();
        prescription.setPrescriptionNo("PRE" + UUID.fastUUID().toString().substring(0, 8).toUpperCase());
        prescription.setMedicalRecordId(dto.getMedicalRecordId());
        prescription.setPatientId(record.getPatientId());
        prescription.setDoctorId(doctor.getId());
        prescription.setStatus("PENDING_REVIEW");
        prescriptionMapper.insert(prescription);

        // 批量插入处方明细
        List<PrescriptionItem> items = new ArrayList<>();
        if (dto.getItems() != null) {
            for (PrescriptionCreateDTO.PrescriptionItemDTO itemDTO : dto.getItems()) {
                PrescriptionItem item = new PrescriptionItem();
                item.setPrescriptionId(prescription.getId());
                item.setDrugId(itemDTO.getDrugId());
                item.setDrugName(itemDTO.getDrugName());
                item.setSpecification(itemDTO.getSpecification());
                item.setDosage(itemDTO.getDosage());
                item.setUsageMethod(itemDTO.getUsageMethod());
                item.setFrequency(itemDTO.getFrequency());
                item.setDays(itemDTO.getDays());
                item.setQuantity(itemDTO.getQuantity());
                item.setUnit(itemDTO.getUnit());
                item.setRemark(itemDTO.getRemark());
                items.add(item);
            }
            prescriptionItemMapper.insertBatch(items);
        }

        List<PrescriptionItem> savedItems = prescriptionItemMapper.selectByPrescriptionId(prescription.getId());
        log.info("[处方] 开具处方: prescriptionId={}, items={}", prescription.getId(), savedItems.size());

        return toPrescriptionVO(prescription, savedItems);
    }

    /**
     * 检查/检验申请（Feign → medsupply-service）
     *
     * @param doctorId 医生 ID
     * @param dto      申请信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void requestExam(Long userId, ExamRequestDTO dto) {
        com.hospital.clinic.entity.Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }

        MedicalRecord record = medicalRecordMapper.selectById(dto.getMedicalRecordId());
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        if (!Objects.equals(record.getDoctorId(), doctor.getId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权为此病历申请检查");
        }

        // 调用 medsupply-service 创建检查申请
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("medicalRecordId", dto.getMedicalRecordId());
        requestBody.put("patientId", record.getPatientId());
        requestBody.put("doctorId", doctor.getId());
        requestBody.put("examItemId", dto.getExamItemId());
        requestBody.put("examItemName", dto.getExamItemName());
        requestBody.put("itemType", dto.getItemType());
        requestBody.put("applyRemark", dto.getApplyRemark());

        try {
            String url = "http://medsupply-service/api/medsupply/internal/exam/apply";
            restTemplate.postForObject(url, requestBody, Map.class);
            log.info("[检查申请] 已发送: recordId={}, examItem={}", dto.getMedicalRecordId(), dto.getExamItemName());
        } catch (Exception e) {
            log.error("[检查申请] 调用 medsupply-service 失败", e);
            throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR, "医辅服务暂不可用");
        }
    }

    /**
     * 结束就诊
     *
     * @param recordId 病历 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void finishConsultation(Long recordId) {
        MedicalRecord record = medicalRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        if (!"SUBMITTED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请先提交病历再结束就诊");
        }

        medicalRecordMapper.updateStatus(recordId, "COMPLETED");
        appointmentMapper.updateVisitStatus(record.getAppointmentId(), "COMPLETED", null);
        log.info("[接诊] 结束就诊: recordId={}, appointmentId={}", recordId, record.getAppointmentId());
    }

    /**
     * 查询病历详情
     */
    public MedicalRecordVO getMedicalRecord(Long recordId) {
        MedicalRecord record = medicalRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        List<Prescription> prescriptions = prescriptionMapper.selectByMedicalRecordId(recordId);
        List<PrescriptionVO> prescriptionVOs = prescriptions.stream()
                .map(p -> {
                    List<PrescriptionItem> items = prescriptionItemMapper.selectByPrescriptionId(p.getId());
                    return toPrescriptionVO(p, items);
                })
                .collect(Collectors.toList());
        return toVO(record, prescriptionVOs);
    }

    /**
     * 查询患者病历列表
     */
    public List<MedicalRecordVO> listByPatient(Long patientId, long offset, Integer limit) {
        List<MedicalRecord> records = medicalRecordMapper.selectByPatientId(patientId, (int) offset, limit);
        return records.stream().map(r -> toVO(r, Collections.emptyList())).collect(Collectors.toList());
    }

    // ==================== 实体 → VO ====================

    private MedicalRecordVO toVO(MedicalRecord r, List<PrescriptionVO> prescriptions) {
        return MedicalRecordVO.builder()
                .id(r.getId())
                .appointmentId(r.getAppointmentId())
                .patientId(r.getPatientId())
                .doctorId(r.getDoctorId())
                .departmentId(r.getDepartmentId())
                .chiefComplaint(r.getChiefComplaint())
                .presentIllness(r.getPresentIllness())
                .pastHistory(r.getPastHistory())
                .temperature(r.getTemperature())
                .pulse(r.getPulse())
                .respiration(r.getRespiration())
                .bloodPressure(r.getBloodPressure())
                .diagnosisCode(r.getDiagnosisCode())
                .diagnosisDesc(r.getDiagnosisDesc())
                .treatmentOpinion(r.getTreatmentOpinion())
                .referralDeptId(r.getReferralDeptId())
                .referralReason(r.getReferralReason())
                .status(r.getStatus())
                .isReturnVisit(r.getIsReturnVisit())
                .prescriptions(prescriptions)
                .createTime(r.getCreateTime())
                .updateTime(r.getUpdateTime())
                .build();
    }

    private PrescriptionVO toPrescriptionVO(Prescription p, List<PrescriptionItem> items) {
        List<PrescriptionItemVO> itemVOs = items.stream()
                .map(i -> PrescriptionItemVO.builder()
                        .id(i.getId()).prescriptionId(i.getPrescriptionId())
                        .drugId(i.getDrugId()).drugName(i.getDrugName())
                        .specification(i.getSpecification()).dosage(i.getDosage())
                        .usageMethod(i.getUsageMethod()).frequency(i.getFrequency())
                        .days(i.getDays()).quantity(i.getQuantity())
                        .unit(i.getUnit()).remark(i.getRemark())
                        .build())
                .collect(Collectors.toList());

        return PrescriptionVO.builder()
                .id(p.getId()).prescriptionNo(p.getPrescriptionNo())
                .medicalRecordId(p.getMedicalRecordId()).patientId(p.getPatientId())
                .doctorId(p.getDoctorId()).status(p.getStatus())
                .reviewComment(p.getReviewComment()).items(itemVOs)
                .createTime(p.getCreateTime()).build();
    }
}
