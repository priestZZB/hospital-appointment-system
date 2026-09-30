package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.FollowUpFeignClient;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.dto.CreateFollowUpDTO;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.AnesthesiaRecordDTO;
import com.hospital.inpatient.dto.InformedConsentDTO;
import com.hospital.inpatient.dto.OutpatientSurgeryDTO;
import com.hospital.inpatient.dto.PreopAssessmentDTO;
import com.hospital.inpatient.dto.SurgeryRecordDTO;
import com.hospital.inpatient.dto.SurgeryScheduleDTO;
import com.hospital.inpatient.entity.AnesthesiaRecord;
import com.hospital.inpatient.entity.InformedConsent;
import com.hospital.inpatient.entity.PreopAssessment;
import com.hospital.inpatient.entity.Surgery;
import com.hospital.inpatient.entity.SurgeryApply;
import com.hospital.inpatient.entity.SurgeryRecord;
import com.hospital.inpatient.mapper.AnesthesiaRecordMapper;
import com.hospital.inpatient.mapper.InformedConsentMapper;
import com.hospital.inpatient.mapper.PreopAssessmentMapper;
import com.hospital.inpatient.mapper.SurgeryMapper;
import com.hospital.inpatient.mapper.SurgeryRecordMapper;
import com.hospital.inpatient.vo.AnesthesiaRecordVO;
import com.hospital.inpatient.vo.InformedConsentVO;
import com.hospital.inpatient.vo.PreopAssessmentVO;
import com.hospital.inpatient.vo.SurgeryBoardVO;
import com.hospital.inpatient.vo.SurgeryDetailVO;
import com.hospital.inpatient.vo.SurgeryRecordVO;
import com.hospital.inpatient.vo.SurgeryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 手术/麻醉中心服务（迭代10 F1~F5，轻量模拟）。
 * <p>
 * 统一手术单状态机：
 * <pre>
 * APPLIED(建单,即生成手术单号) --排台--> SCHEDULED --术前评估PASSED--> PREOP_PASSED
 *      |                                |                              |
 *      |                                +--(未评估也可开始,记warning)--+--开始--> IN_OPERATION
 *      +--取消--> CANCELLED <--取消------+                                     | 手术记录
 *                                                                             v OPERATED --术后随访
 * </pre>
 * 各流转均以带前置状态条件的 UPDATE 落库（affected=0 即状态不匹配，回抛业务异常）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeryService {

    private static final DateTimeFormatter SIGN_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> CONSENT_ALLOWED_STATUS = Set.of("APPLIED", "SCHEDULED", "PREOP_PASSED");
    private static final Set<String> ANESTHESIA_ALLOWED_STATUS = Set.of("SCHEDULED", "PREOP_PASSED", "IN_OPERATION", "OPERATED");

    private final SurgeryMapper surgeryMapper;
    private final PreopAssessmentMapper preopAssessmentMapper;
    private final InformedConsentMapper informedConsentMapper;
    private final SurgeryRecordMapper surgeryRecordMapper;
    private final AnesthesiaRecordMapper anesthesiaRecordMapper;
    private final FollowUpFeignClient followUpFeignClient;
    private final PatientFeignClient patientFeignClient;

    // ==================== F1 门诊手术建单 ====================

    /** F1：门诊手术建单（source=OUTPATIENT，status=APPLIED，建单即生成手术单号） */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryVO createOutpatient(OutpatientSurgeryDTO dto) {
        Surgery surgery = new Surgery();
        surgery.setSurgeryNo(surgeryMapper.nextSurgeryNo());
        surgery.setSource("OUTPATIENT");
        surgery.setPatientId(dto.getPatientId());
        surgery.setApplyDoctorId(dto.getApplyDoctorId());
        surgery.setSurgeryName(dto.getSurgeryName());
        surgery.setSurgeryType(dto.getSurgeryType());
        surgery.setAppointmentId(dto.getAppointmentId());
        surgery.setNotes(dto.getNotes());
        surgery.setStatus("APPLIED");
        surgeryMapper.insert(surgery);
        log.info("[手术中心] 门诊手术建单: id={}, no={}, patientId={}, name={}",
                surgery.getId(), surgery.getSurgeryNo(), dto.getPatientId(), dto.getSurgeryName());
        return toVO(requireSurgery(surgery.getId()));
    }

    // ==================== F2 统一手术单排台 ====================

    /**
     * F2：排台（APPLIED → SCHEDULED）。
     * 同时落排台时间/手术间/主刀/麻醉方式/麻醉医生。
     */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryVO schedule(Long id, SurgeryScheduleDTO dto) {
        Surgery surgery = requireSurgery(id);
        if (!"APPLIED".equals(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "仅待排台(APPLIED)的手术单可排台，当前状态: " + surgery.getStatus());
        }
        if (surgeryMapper.schedule(id, dto.getScheduledTime(), dto.getOperatingRoom(),
                dto.getSurgeonId(), dto.getAnesthesiaMethod(), dto.getAnesthesiologistId()) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "手术单状态已变更，排台失败");
        }
        log.info("[手术中心] 排台: id={}, no={}, time={}, room={}, surgeonId={}",
                id, surgery.getSurgeryNo(), dto.getScheduledTime(), dto.getOperatingRoom(), dto.getSurgeonId());
        return toVO(requireSurgery(id));
    }

    /** 撤台/取消（APPLIED / SCHEDULED → CANCELLED，补全状态机闭环） */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryVO cancel(Long id) {
        Surgery surgery = requireSurgery(id);
        if (!"APPLIED".equals(surgery.getStatus()) && !"SCHEDULED".equals(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "仅待排台/已排台的手术单可取消，当前状态: " + surgery.getStatus());
        }
        if (surgeryMapper.markCancelled(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "手术单状态已变更，取消失败");
        }
        log.info("[手术中心] 取消手术单: id={}, no={}", id, surgery.getSurgeryNo());
        return toVO(requireSurgery(id));
    }

    // ==================== F3 术前评估 + 知情同意 ====================

    /**
     * F3：提交术前评估（UNIQUE(surgery_id) 防重，重复提交覆盖更新）。
     * 结论 PASSED 且手术单处于 SCHEDULED 时推进为 PREOP_PASSED；
     * APPLIED（尚未排台）时仅记录评估，待排台后再评估通过或重新提交即可推进，
     * 避免提前评估把单锁死在 PREOP_PASSED 导致无法排台。
     */
    @Transactional(rollbackFor = Exception.class)
    public PreopAssessmentVO submitPreop(Long id, PreopAssessmentDTO dto, Long assessorId) {
        Surgery surgery = requireSurgery(id);
        if (Set.of("IN_OPERATION", "OPERATED", "CANCELLED").contains(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "手术已开始/完成或已取消，不可提交术前评估，当前状态: " + surgery.getStatus());
        }
        PreopAssessment assessment = new PreopAssessment();
        assessment.setSurgeryId(id);
        assessment.setAsaGrade(dto.getAsaGrade());
        assessment.setRiskFactors(dto.getRiskFactors());
        assessment.setAssessmentText(dto.getAssessmentText());
        assessment.setConclusion(dto.getConclusion());
        assessment.setAssessorId(assessorId);
        if (preopAssessmentMapper.updateBySurgeryId(assessment) == 0) {
            preopAssessmentMapper.insert(assessment);
        }
        if ("PASSED".equals(dto.getConclusion()) && "SCHEDULED".equals(surgery.getStatus())) {
            if (surgeryMapper.markPreopPassed(id) == 0) {
                log.warn("[手术中心] 评估通过但状态推进失败(并发): surgeryId={}", id);
            }
        }
        log.info("[手术中心] 术前评估: surgeryId={}, asa={}, conclusion={}", id, dto.getAsaGrade(), dto.getConclusion());
        return toVO(preopAssessmentMapper.selectBySurgeryId(id));
    }

    /**
     * F3：签署知情同意书（SURGERY / ANESTHESIA 各一条，重复签署覆盖更新）。
     * patientSign 入参为患者姓名，落库为 姓名+（已签署）+时间戳 模拟签名文本。
     */
    @Transactional(rollbackFor = Exception.class)
    public InformedConsentVO signConsent(Long id, InformedConsentDTO dto) {
        Surgery surgery = requireSurgery(id);
        if (!CONSENT_ALLOWED_STATUS.contains(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "手术已开始/完成或已取消，不可签署知情同意书，当前状态: " + surgery.getStatus());
        }
        InformedConsent consent = new InformedConsent();
        consent.setSurgeryId(id);
        consent.setConsentType(dto.getConsentType());
        consent.setPatientSign(truncate(dto.getPatientSign() + "（已签署）" + LocalDateTime.now().format(SIGN_TS), 100));
        consent.setWitness(dto.getWitness());
        if (informedConsentMapper.updateBySurgeryAndType(consent) == 0) {
            informedConsentMapper.insert(consent);
        }
        log.info("[手术中心] 知情同意签署: surgeryId={}, type={}", id, dto.getConsentType());
        return toVO(informedConsentMapper.selectBySurgeryAndType(id, dto.getConsentType()));
    }

    // ==================== F4 手术开始 / 手术记录 / 麻醉记录 ====================

    /**
     * F4：手术开始（SCHEDULED / PREOP_PASSED → IN_OPERATION）。
     * 简化规则：未做术前评估（SCHEDULED）也可开始，但记录 warning 日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryVO start(Long id) {
        Surgery surgery = requireSurgery(id);
        if ("SCHEDULED".equals(surgery.getStatus())) {
            log.warn("[手术中心] 手术未做术前评估(或有条件通过)即开始（简化放行）: id={}, no={}",
                    id, surgery.getSurgeryNo());
        } else if (!"PREOP_PASSED".equals(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "仅已排台(SCHEDULED)或术前评估通过(PREOP_PASSED)的手术可开始，当前状态: " + surgery.getStatus());
        }
        if (surgeryMapper.markInOperation(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "手术单状态已变更，开始失败");
        }
        log.info("[手术中心] 手术开始: id={}, no={}, room={}", id, surgery.getSurgeryNo(), surgery.getOperatingRoom());
        return toVO(requireSurgery(id));
    }

    /**
     * F4：手术记录录入（要求 IN_OPERATION；UNIQUE(surgery_id) 一单一条；
     * 录入完成即推进 OPERATED，时长回写主单）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryRecordVO saveRecord(Long id, SurgeryRecordDTO dto) {
        Surgery surgery = requireSurgery(id);
        if (!"IN_OPERATION".equals(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "仅手术中(IN_OPERATION)可录入手术记录，当前状态: " + surgery.getStatus());
        }
        if (surgeryRecordMapper.selectBySurgeryId(id) != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "手术记录已录入，不可重复录入");
        }
        SurgeryRecord record = new SurgeryRecord();
        record.setSurgeryId(id);
        record.setSurgeonId(surgery.getSurgeonId());
        record.setIncision(dto.getIncision());
        record.setProcedureText(dto.getProcedureText());
        record.setFindings(dto.getFindings());
        record.setSpecimenFlag(dto.getSpecimenFlag());
        record.setBloodLossMl(dto.getBloodLossMl());
        record.setDurationMin(dto.getDurationMin());
        surgeryRecordMapper.insert(record);
        if (surgeryMapper.markOperated(id, dto.getDurationMin()) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "手术单状态已变更，完成失败");
        }
        log.info("[手术中心] 手术记录录入并完成: surgeryId={}, no={}, specimenFlag={}",
                id, surgery.getSurgeryNo(), dto.getSpecimenFlag());
        return toVO(surgeryRecordMapper.selectBySurgeryId(id));
    }

    /**
     * F4：麻醉记录录入（一单一条；要求手术已排台且未取消——
     * 允许术中录入与术后补录，术前评估/排台阶段亦可提前登记）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AnesthesiaRecordVO saveAnesthesia(Long id, AnesthesiaRecordDTO dto) {
        Surgery surgery = requireSurgery(id);
        if (!ANESTHESIA_ALLOWED_STATUS.contains(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "手术未排台或已取消，不可录入麻醉记录，当前状态: " + surgery.getStatus());
        }
        if (anesthesiaRecordMapper.selectBySurgeryId(id) != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "麻醉记录已录入，不可重复录入");
        }
        AnesthesiaRecord record = new AnesthesiaRecord();
        record.setSurgeryId(id);
        record.setMethod(dto.getMethod());
        record.setAsaGrade(dto.getAsaGrade());
        record.setInductionTime(dto.getInductionTime());
        record.setReversalTime(dto.getReversalTime());
        record.setVitalsJson(dto.getVitals());
        record.setAnesthesiologistId(dto.getAnesthesiologistId() != null
                ? dto.getAnesthesiologistId() : surgery.getAnesthesiologistId());
        record.setNotes(dto.getNotes());
        anesthesiaRecordMapper.insert(record);
        log.info("[手术中心] 麻醉记录录入: surgeryId={}, no={}, method={}", id, surgery.getSurgeryNo(), dto.getMethod());
        return toVO(anesthesiaRecordMapper.selectBySurgeryId(id));
    }

    // ==================== F5 术后随访衔接 ====================

    /**
     * F5：术后随访触发（要求 OPERATED）。
     * 通过 FollowUpFeignClient（clinic-service /api/clinic/internal/follow-up）创建
     * 「术后镇痛随访」电话随访，fail-open：Feign 调用失败仅记日志并返回提示，不抛错。
     */
    public Map<String, Object> postopFollowup(Long id, Long operatorUserId) {
        Surgery surgery = requireSurgery(id);
        if (!"OPERATED".equals(surgery.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION,
                    "仅已完成(OPERATED)的手术可触发术后随访，当前状态: " + surgery.getStatus());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("surgeryId", id);
        result.put("surgeryNo", surgery.getSurgeryNo());
        try {
            CreateFollowUpDTO dto = new CreateFollowUpDTO();
            dto.setPatientId(surgery.getPatientId());
            dto.setDoctorUserId(operatorUserId);
            dto.setFollowDate(LocalDate.now().plusDays(1));
            dto.setFollowMethod("PHONE");
            dto.setTemplate("术后镇痛随访");
            Result<Map<String, Object>> resp = followUpFeignClient.createFollowUp(dto);
            if (resp != null && resp.getCode() != null && resp.getCode() == 0 && resp.getData() != null) {
                result.put("followUpCreated", true);
                result.put("planId", resp.getData().get("planId"));
                result.put("planStatus", resp.getData().get("status"));
                result.put("message", "术后镇痛随访计划创建成功");
                log.info("[手术中心] 术后随访衔接成功: surgeryId={}, patientId={}, planId={}",
                        id, surgery.getPatientId(), resp.getData().get("planId"));
            } else {
                result.put("followUpCreated", false);
                result.put("message", "随访服务返回失败（fail-open 不阻断），请稍后手工创建随访计划");
                log.warn("[手术中心] 术后随访衔接返回失败: surgeryId={}, resp={}", id, resp);
            }
        } catch (Exception e) {
            // fail-open：随访失败不阻断主流程
            result.put("followUpCreated", false);
            result.put("message", "随访服务暂不可用（fail-open 不阻断），请稍后手工创建随访计划");
            log.warn("[手术中心] 术后随访衔接失败（忽略）: surgeryId={}, error={}", id, e.getMessage());
        }
        return result;
    }

    // ==================== 查询：看板 / 详情 / 分页 ====================

    /** 手术排台看板（当日列表：手术/患者/主刀/麻醉方式/状态/评估结论；date 缺省今天） */
    public List<SurgeryBoardVO> board(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<SurgeryBoardVO> rows = surgeryMapper.selectBoard(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        fillPatientNames(rows);
        return rows;
    }

    /** 详情聚合：手术单 + 术前评估 + 双同意书签署状态 + 手术记录 + 麻醉记录 */
    public SurgeryDetailVO detail(Long id) {
        Surgery surgery = requireSurgery(id);
        SurgeryDetailVO detail = new SurgeryDetailVO();
        detail.setSurgery(toVO(surgery));
        PreopAssessment preop = preopAssessmentMapper.selectBySurgeryId(id);
        detail.setPreop(preop == null ? null : toVO(preop));
        detail.setConsentSurgery(toConsentVO(informedConsentMapper.selectBySurgeryAndType(id, "SURGERY"), id, "SURGERY"));
        detail.setConsentAnesthesia(toConsentVO(informedConsentMapper.selectBySurgeryAndType(id, "ANESTHESIA"), id, "ANESTHESIA"));
        SurgeryRecord record = surgeryRecordMapper.selectBySurgeryId(id);
        detail.setRecord(record == null ? null : toVO(record));
        AnesthesiaRecord anesthesia = anesthesiaRecordMapper.selectBySurgeryId(id);
        detail.setAnesthesia(anesthesia == null ? null : toVO(anesthesia));
        detail.setLoadTime(LocalDateTime.now());
        return detail;
    }

    /** 分页查询（status/source 筛选） */
    public Map<String, Object> page(String status, String source, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (no - 1) * size;
        List<SurgeryVO> records = surgeryMapper.selectPage(status, source, offset, size)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = surgeryMapper.countPage(status, source);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", no);
        result.put("pageSize", size);
        return result;
    }

    // ==================== F2：住院手术申请排台联动（供 SurgeryApplyService 调用，加入其事务） ====================

    /**
     * 住院手术申请排台成功后同步创建统一手术单（source=INPATIENT，apply_id 关联）。
     * 传播行为 REQUIRED：加入调用方 SurgeryApplyService.schedule 的事务，
     * 本插入失败则整体回滚（申请单排台一并回滚）；已存在关联单时幂等返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public Surgery createFromApply(SurgeryApply apply) {
        Surgery existing = surgeryMapper.selectByApplyId(apply.getId());
        if (existing != null) {
            log.info("[手术中心] 住院排台关联手术单已存在(幂等返回): applyId={}, surgeryId={}",
                    apply.getId(), existing.getId());
            return existing;
        }
        Surgery surgery = new Surgery();
        surgery.setSurgeryNo(surgeryMapper.nextSurgeryNo());
        surgery.setSource("INPATIENT");
        surgery.setPatientId(apply.getPatientId());
        surgery.setApplyDoctorId(apply.getApplyDoctorId());
        surgery.setSurgeryName(apply.getSurgeryName());
        surgery.setAnesthesiaMethod(apply.getAnesthesiaType());
        surgery.setScheduledTime(apply.getScheduledTime());
        surgery.setOperatingRoom(apply.getOperatingRoom());
        surgery.setStatus("SCHEDULED");
        surgery.setApplyId(apply.getId());
        surgery.setNotes(apply.getRemark());
        surgeryMapper.insert(surgery);
        log.info("[手术中心] 住院排台同步建单: id={}, no={}, applyId={}",
                surgery.getId(), surgery.getSurgeryNo(), apply.getId());
        return surgery;
    }

    /** 按 applyId 查询关联统一手术单（申请单列表回查 surgeryId 用） */
    public Surgery findByApplyId(Long applyId) {
        return surgeryMapper.selectByApplyId(applyId);
    }

    /**
     * 住院申请单取消联动撤台（best-effort）：统一手术单仍在 APPLIED/SCHEDULED 时同步 CANCELLED，
     * 已开始/完成不强撤仅告警；加入调用方事务，不阻断申请单取消主流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public Surgery syncCancelFromApply(Long applyId) {
        Surgery surgery = surgeryMapper.selectByApplyId(applyId);
        if (surgery == null) {
            return null;
        }
        if ("APPLIED".equals(surgery.getStatus()) || "SCHEDULED".equals(surgery.getStatus())) {
            if (surgeryMapper.markCancelled(surgery.getId()) > 0) {
                log.info("[手术中心] 申请单取消联动撤台: applyId={}, surgeryId={}, no={}",
                        applyId, surgery.getId(), surgery.getSurgeryNo());
            } else {
                log.warn("[手术中心] 申请单取消联动撤台失败(并发): applyId={}, surgeryId={}",
                        applyId, surgery.getId());
            }
            return surgeryMapper.selectById(surgery.getId());
        }
        log.warn("[手术中心] 申请单取消时手术单已开始/完成，不强撤: applyId={}, surgeryId={}, status={}",
                applyId, surgery.getId(), surgery.getStatus());
        return surgery;
    }

    // ==================== 私有辅助 ====================

    private Surgery requireSurgery(Long id) {
        Surgery surgery = surgeryMapper.selectById(id);
        if (surgery == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "手术单不存在");
        }
        return surgery;
    }

    /** 患者姓名批量补齐（fail-open：Feign 失败仅告警） */
    private void fillPatientNames(List<SurgeryBoardVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(SurgeryBoardVO::getPatientId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> batch = patientFeignClient.getBatch(ids);
            if (batch == null) {
                return;
            }
            Map<Long, String> names = new HashMap<>();
            for (Map<String, Object> info : batch) {
                Object pid = info.get("id");
                Object name = info.get("name");
                if (pid != null && name != null) {
                    names.put(((Number) pid).longValue(), String.valueOf(name));
                }
            }
            rows.forEach(r -> r.setPatientName(names.get(r.getPatientId())));
        } catch (Exception e) {
            log.warn("[手术中心] 患者姓名批量查询失败（忽略）: {}", e.getMessage());
        }
    }

    private InformedConsentVO toConsentVO(InformedConsent consent, Long surgeryId, String type) {
        InformedConsentVO vo = new InformedConsentVO();
        vo.setSurgeryId(surgeryId);
        vo.setConsentType(type);
        if (consent == null) {
            vo.setSigned(false);
            return vo;
        }
        vo.setId(consent.getId());
        vo.setSigned(true);
        vo.setPatientSign(consent.getPatientSign());
        vo.setSignedTime(consent.getSignedTime());
        vo.setWitness(consent.getWitness());
        vo.setCreateTime(consent.getCreateTime());
        return vo;
    }

    private SurgeryVO toVO(Surgery s) {
        SurgeryVO vo = new SurgeryVO();
        vo.setId(s.getId());
        vo.setSurgeryNo(s.getSurgeryNo());
        vo.setSource(s.getSource());
        vo.setPatientId(s.getPatientId());
        vo.setApplyDoctorId(s.getApplyDoctorId());
        vo.setSurgeryName(s.getSurgeryName());
        vo.setSurgeryType(s.getSurgeryType());
        vo.setScheduledTime(s.getScheduledTime());
        vo.setOperatingRoom(s.getOperatingRoom());
        vo.setSurgeonId(s.getSurgeonId());
        vo.setAnesthesiaMethod(s.getAnesthesiaMethod());
        vo.setAnesthesiologistId(s.getAnesthesiologistId());
        vo.setDurationMin(s.getDurationMin());
        vo.setStatus(s.getStatus());
        vo.setApplyId(s.getApplyId());
        vo.setAppointmentId(s.getAppointmentId());
        vo.setNotes(s.getNotes());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        return vo;
    }

    private PreopAssessmentVO toVO(PreopAssessment a) {
        PreopAssessmentVO vo = new PreopAssessmentVO();
        vo.setId(a.getId());
        vo.setSurgeryId(a.getSurgeryId());
        vo.setAsaGrade(a.getAsaGrade());
        vo.setRiskFactors(a.getRiskFactors());
        vo.setAssessmentText(a.getAssessmentText());
        vo.setConclusion(a.getConclusion());
        vo.setAssessorId(a.getAssessorId());
        vo.setAssessmentTime(a.getAssessmentTime());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }

    private SurgeryRecordVO toVO(SurgeryRecord r) {
        SurgeryRecordVO vo = new SurgeryRecordVO();
        vo.setId(r.getId());
        vo.setSurgeryId(r.getSurgeryId());
        vo.setSurgeonId(r.getSurgeonId());
        vo.setIncision(r.getIncision());
        vo.setProcedureText(r.getProcedureText());
        vo.setFindings(r.getFindings());
        vo.setSpecimenFlag(r.getSpecimenFlag());
        vo.setBloodLossMl(r.getBloodLossMl());
        vo.setDurationMin(r.getDurationMin());
        vo.setRecordTime(r.getRecordTime());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private AnesthesiaRecordVO toVO(AnesthesiaRecord r) {
        AnesthesiaRecordVO vo = new AnesthesiaRecordVO();
        vo.setId(r.getId());
        vo.setSurgeryId(r.getSurgeryId());
        vo.setMethod(r.getMethod());
        vo.setAsaGrade(r.getAsaGrade());
        vo.setInductionTime(r.getInductionTime());
        vo.setReversalTime(r.getReversalTime());
        vo.setVitals(r.getVitalsJson());
        vo.setAnesthesiologistId(r.getAnesthesiologistId());
        vo.setNotes(r.getNotes());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private InformedConsentVO toVO(InformedConsent c) {
        return toConsentVO(c, c.getSurgeryId(), c.getConsentType());
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
