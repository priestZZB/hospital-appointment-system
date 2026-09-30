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
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.PharmacyFeignClient;
import com.hospital.common.feign.dto.PharmacyCheckDTO;
import com.hospital.common.feign.dto.PharmacyCheckResult;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.util.DataScopeUtil;
import com.hospital.common.util.DataScopeUtil.ScopeType;
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
    private final IcdDictService icdDictService;
    private final PatientFeignClient patientFeignClient;
    private final PharmacyFeignClient pharmacyFeignClient;
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
        String visitStatus = appointment.getVisitStatus();
        if ("IN_PROGRESS".equals(visitStatus)) {
            throw new BusinessException(ErrorCodeEnum.CONSULTATION_IN_PROGRESS);
        }
        if ("COMPLETED".equals(visitStatus)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已完成就诊，不可重复接诊");
        }
        if (!("CHECKED_IN".equals(visitStatus) || "CALLED".equals(visitStatus))) {
            throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_CHECKED_IN, "患者未签到或未叫号，无法接诊");
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
            // ICD-10 诊断编码校验（迭代9 J3）：diagnosisCode 非空时必须在 icd_dict 中存在；
            // 字典查询异常时 fail-open 放行（见 IcdDictService.validateCode），不阻塞诊疗流程
            if (record.getDiagnosisCode() != null && !record.getDiagnosisCode().isBlank()) {
                icdDictService.validateCode(record.getDiagnosisCode());
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
     * 创建 prescription + prescription_item。
     * 开方安全校验链（迭代7 B9/B10）：
     * ① HERBAL 处方必须指定剂数；② CDSS 安全审查（BLOCK 拦截 / WARN 放行）；
     * ③ 抗菌药物处方授权校验；④ medsupply 不可用时 fail-open 放行，不阻塞开方。
     *
     * @param userId 登录用户 ID（映射医生档案）
     * @param dto    处方信息
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

        // ==================== 开方安全校验链（迭代7 B9/B10）====================
        // 处方类型归一化：缺省为西药/中成药处方笺
        String prescriptionType = dto.getPrescriptionType() == null || dto.getPrescriptionType().isBlank()
                ? "WESTERN" : dto.getPrescriptionType();
        if (!"WESTERN".equals(prescriptionType) && !"HERBAL".equals(prescriptionType)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "无效的处方类型: " + prescriptionType);
        }
        // ① 中药饮片处方必须指定剂数（明细药品是否均为 drug_type=HERBAL 由 medsupply 侧把关）
        if ("HERBAL".equals(prescriptionType) && dto.getHerbalDoses() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "中药处方必须指定剂数");
        }
        // ② CDSS 处方安全审查（BLOCK 拦截 / WARN 放行）
        // ③ 抗菌药物处方授权校验（passed=false 拦截）
        // ④ medsupply 不可用或响应不可解析时 fail-open 放行，不阻塞开方
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            PharmacyCheckDTO checkDTO = new PharmacyCheckDTO();
            checkDTO.setPatientId(record.getPatientId());
            checkDTO.setDoctorId(doctor.getId());
            checkDTO.setItems(dto.getItems().stream()
                    .map(i -> new PharmacyCheckDTO.Item(i.getDrugId(), i.getDrugName(),
                            i.getDosage(), i.getQuantity(), i.getDays()))
                    .collect(Collectors.toList()));
            checkCdss(checkDTO);
            checkAntibioticAuthorization(checkDTO);
        }

        // 划价：按明细 Σ(单价 × 数量) 计算处方总金额
        java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;
        if (dto.getItems() != null) {
            for (PrescriptionCreateDTO.PrescriptionItemDTO itemDTO : dto.getItems()) {
                java.math.BigDecimal price = itemDTO.getPrice() == null
                        ? java.math.BigDecimal.ZERO : itemDTO.getPrice();
                int qty = itemDTO.getQuantity() == null ? 1 : itemDTO.getQuantity();
                totalAmount = totalAmount.add(price.multiply(java.math.BigDecimal.valueOf(qty)));
            }
        }

        // 创建处方主表（含缴费状态与总金额）
        Prescription prescription = new Prescription();
        prescription.setPrescriptionNo("PRE" + UUID.fastUUID().toString().substring(0, 8).toUpperCase());
        prescription.setMedicalRecordId(dto.getMedicalRecordId());
        prescription.setPatientId(record.getPatientId());
        prescription.setDoctorId(doctor.getId());
        prescription.setStatus("PENDING_REVIEW");
        prescription.setPayStatus("UNPAID");
        prescription.setTotalAmount(totalAmount);
        prescription.setPrescriptionType(prescriptionType);
        prescription.setHerbalDoses(dto.getHerbalDoses());
        prescription.setHerbalUsage(dto.getHerbalUsage());
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
                item.setUnitPrice(itemDTO.getPrice() == null ? java.math.BigDecimal.ZERO : itemDTO.getPrice());
                item.setUnit(itemDTO.getUnit());
                item.setRemark(itemDTO.getRemark());
                item.setDecoctionMethod(itemDTO.getDecoctionMethod());
                item.setFootnote(itemDTO.getFootnote());
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
        // 仅进行中（IN_PROGRESS）可结束，防止覆盖已结束/已取消的状态
        appointmentMapper.updateVisitStatus(record.getAppointmentId(), "COMPLETED", "IN_PROGRESS");
        log.info("[接诊] 结束就诊: recordId={}, appointmentId={}", recordId, record.getAppointmentId());
    }

    /**
     * 查询病历详情
     * <p>
     * 数据范围（迭代 5 阶段 3）：医师（含科主任）=仅本人开具的病历；
     * 管理员/超管=全量；患者=仅本人。
     */
    public MedicalRecordVO getMedicalRecord(Long recordId, Long userId) {
        MedicalRecord record = medicalRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        // 数据范围：医师 → 仅本人病历
        ScopeType scope = DataScopeUtil.getScopeType();
        if (scope == ScopeType.SELF) {
            com.hospital.clinic.entity.Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor == null || !Objects.equals(record.getDoctorId(), doctor.getId())) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅可查看本人开具的病历");
            }
        } else {
            checkRecordOwner(record, userId);
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
     * <p>
     * 数据范围（迭代 5 阶段 3）：管理员/超管=全量；医师（含科主任）=仅本人开具的病历；
     * 患者=仅本人；其余角色按原逻辑（医生或管理员可查任意，否则仅本人）。
     */
    public List<MedicalRecordVO> listByPatient(Long patientId, long offset, Integer limit, Long userId) {
        // 数据范围：医师 → 仅本人病历
        ScopeType scope = DataScopeUtil.getScopeType();
        if (scope == ScopeType.SELF) {
            com.hospital.clinic.entity.Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor == null) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "医师账号未关联医生档案");
            }
            List<MedicalRecord> myRecords = medicalRecordMapper.selectByPatientIdAndDoctorId(
                    patientId, doctor.getId(), (int) offset, limit);
            return myRecords.stream().map(r -> toVO(r, Collections.emptyList())).collect(Collectors.toList());
        }

        // 原逻辑：管理员/医生可查任意患者，患者仅可查本人病历
        if (!UserContext.isDoctorOrAdmin()) {
            Long myPatientId = resolvePatientId(userId);
            if (myPatientId == null || !myPatientId.equals(patientId)) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权查看他人病历");
            }
        }
        List<MedicalRecord> records = medicalRecordMapper.selectByPatientId(patientId, (int) offset, limit);
        return records.stream().map(r -> toVO(r, Collections.emptyList())).collect(Collectors.toList());
    }

    /**
     * 病历归属校验：管理员/医生可查看，患者仅可查看本人病历
     */
    private void checkRecordOwner(MedicalRecord record, Long userId) {
        if (UserContext.isDoctorOrAdmin()) {
            return;
        }
        Long myPatientId = resolvePatientId(userId);
        if (myPatientId == null || !myPatientId.equals(record.getPatientId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权查看他人病历");
        }
    }

    /**
     * 通过 userId 解析 patientId（auth userId ≠ patient db id）
     */
    private Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                return null;
            }
            Object pidObj = patientInfo.get("id");
            if (pidObj == null) {
                return null;
            }
            return Long.valueOf(pidObj.toString());
        } catch (Exception e) {
            log.warn("[接诊] 查询患者信息失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * CDSS 处方安全审查（medsupply-service，迭代7 B9）
     * <p>
     * 存在 severity=BLOCK 的违规（或远端明确 passed=false）→ 拦截开方；
     * 仅 WARN → 放行并记录日志；远端不可用或响应不可解析 → fail-open 放行，不阻塞开方。
     */
    private void checkCdss(PharmacyCheckDTO checkDTO) {
        try {
            Map<String, Object> resp = pharmacyFeignClient.cdssCheck(checkDTO);
            PharmacyCheckResult result = PharmacyCheckResult.fromMap(resp);
            if (result == null) {
                log.warn("[处方] CDSS 响应不可解析，fail-open 放行: patientId={}", checkDTO.getPatientId());
                return;
            }
            List<PharmacyCheckResult.Violation> blocks = result.getBlockViolations();
            if (!blocks.isEmpty()) {
                String detail = blocks.stream()
                        .map(v -> {
                            String text = v.getDescription() == null || v.getDescription().isBlank()
                                    ? (v.getRuleType() == null || v.getRuleType().isBlank()
                                    ? "用药安全违规" : v.getRuleType())
                                    : v.getDescription();
                            return v.getDrugName() == null || v.getDrugName().isBlank()
                                    ? text : v.getDrugName() + "：" + text;
                        })
                        .collect(Collectors.joining("；"));
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "CDSS拦截：" + detail);
            }
            if (Boolean.FALSE.equals(result.getPassed())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "CDSS拦截："
                        + (result.getMessage() == null || result.getMessage().isBlank()
                        ? "处方未通过用药安全审查" : result.getMessage()));
            }
            List<PharmacyCheckResult.Violation> warns = result.getWarnViolations();
            if (!warns.isEmpty()) {
                log.info("[处方] CDSS 警告（放行）: patientId={}, warnings={}", checkDTO.getPatientId(),
                        warns.stream()
                                .map(v -> (v.getRuleType() == null ? "" : v.getRuleType() + ":")
                                        + (v.getDescription() == null ? "" : v.getDescription()))
                                .collect(Collectors.joining("；")));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[处方] CDSS 校验服务不可用，fail-open 放行: patientId={}", checkDTO.getPatientId(), e);
        }
    }

    /**
     * 抗菌药物处方授权校验（medsupply-service，迭代7 B10）
     * <p>
     * passed=false → 拦截开方；远端不可用或响应不可解析 → fail-open 放行，不阻塞开方。
     */
    private void checkAntibioticAuthorization(PharmacyCheckDTO checkDTO) {
        try {
            Map<String, Object> resp = pharmacyFeignClient.antibioticCheck(checkDTO);
            PharmacyCheckResult result = PharmacyCheckResult.fromMap(resp);
            if (result == null) {
                log.warn("[处方] 抗菌药物授权校验响应不可解析，fail-open 放行: doctorId={}", checkDTO.getDoctorId());
                return;
            }
            if (Boolean.FALSE.equals(result.getPassed())) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "抗菌药物授权不足："
                        + (result.getMessage() == null || result.getMessage().isBlank()
                        ? "当前医生无抗菌药物处方权限" : result.getMessage()));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[处方] 抗菌药物授权校验服务不可用，fail-open 放行: doctorId={}", checkDTO.getDoctorId(), e);
        }
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
                        .unitPrice(i.getUnitPrice())
                        .unit(i.getUnit()).remark(i.getRemark())
                        .decoctionMethod(i.getDecoctionMethod()).footnote(i.getFootnote())
                        .build())
                .collect(Collectors.toList());

        return PrescriptionVO.builder()
                .id(p.getId()).prescriptionNo(p.getPrescriptionNo())
                .medicalRecordId(p.getMedicalRecordId()).patientId(p.getPatientId())
                .doctorId(p.getDoctorId()).status(p.getStatus())
                .reviewComment(p.getReviewComment())
                .totalAmount(p.getTotalAmount()).payStatus(p.getPayStatus())
                .prescriptionType(p.getPrescriptionType())
                .herbalDoses(p.getHerbalDoses()).herbalUsage(p.getHerbalUsage())
                .items(itemVOs)
                .createTime(p.getCreateTime()).build();
    }
}
