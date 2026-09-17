package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.FollowUpFeignClient;
import com.hospital.common.feign.dto.CreateFollowUpDTO;
import com.hospital.inpatient.dto.DischargeDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.Bed;
import com.hospital.inpatient.entity.DischargeSummary;
import com.hospital.inpatient.entity.MedicalRecordHome;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.BedMapper;
import com.hospital.inpatient.mapper.BedOccupancyMapper;
import com.hospital.inpatient.mapper.DepositMapper;
import com.hospital.inpatient.mapper.DischargeSummaryMapper;
import com.hospital.inpatient.mapper.InpatientFeeMapper;
import com.hospital.inpatient.mapper.MedicalRecordHomeMapper;
import com.hospital.inpatient.vo.DischargeSummaryVO;
import com.hospital.inpatient.vo.MedicalRecordHomeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出院服务：出院小结 + 结算试算（多退少补）+ 退床 + 状态回写。
 * 结算金额 = 总费用 - 预交金余额：正数应补缴、负数应退还。
 * 后续补缴/退款后可重新结算（updateSettlement 幂等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DischargeService {

    private final AdmissionMapper admissionMapper;
    private final BedMapper bedMapper;
    private final BedOccupancyMapper bedOccupancyMapper;
    private final DepositMapper depositMapper;
    private final InpatientFeeMapper feeMapper;
    private final DischargeSummaryMapper dischargeSummaryMapper;
    private final MedicalRecordHomeMapper homeMapper;
    private final FollowUpFeignClient followUpFeignClient;
    private final InpatientSupport support;

    /** 办理出院（医生）：写出院小结 + 结算试算 + 退床 + 状态 DISCHARGED */
    @Transactional(rollbackFor = Exception.class)
    public DischargeSummaryVO discharge(DischargeDTO dto, Long doctorUserId) {
        Admission admission = requireAdmission(dto.getAdmissionId());
        if (!"ADMITTED".equals(admission.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已出院，不可重复办理");
        }
        DischargeSummary summary = new DischargeSummary();
        summary.setAdmissionId(admission.getId());
        summary.setAdmissionDiag(admission.getAdmissionDiag());
        summary.setDischargeDiag(dto.getDischargeDiag());
        summary.setTreatmentProcess(dto.getTreatmentProcess());
        summary.setDischargeCondition(dto.getDischargeCondition());
        summary.setDischargeAdvice(dto.getDischargeAdvice());
        summary.setDoctorId(doctorUserId);
        summary.setDischargeTime(LocalDateTime.now());
        // 结算试算：多退少补
        BigDecimal totalFee = InpatientSupport.nz(feeMapper.sumByAdmission(admission.getId()));
        BigDecimal depositBalance = InpatientSupport.nz(depositMapper.sumByAdmission(admission.getId()));
        summary.setDepositBalance(depositBalance);
        summary.setSettlementAmount(totalFee.subtract(depositBalance));
        dischargeSummaryMapper.insert(summary);
        // 退床释放
        Bed bed = bedOccupancyMapper.selectCurrentBed(admission.getId());
        if (bed != null) {
            bedOccupancyMapper.closeCurrent(admission.getId());
            bedMapper.updateStatus(bed.getId(), "AVAILABLE");
        }
        admissionMapper.updateStatus(admission.getId(), "DISCHARGED");
        // E3：病案首页归档
        archiveHome(admission, summary);
        // E4：出院自动衔接随访（fail-open，不阻断出院）
        linkFollowUp(admission, doctorUserId);
        log.info("[住院出院] admissionId={}, 结算金额={}, 预交金余额={}",
                admission.getId(), summary.getSettlementAmount(), depositBalance);
        return toVO(summary, admission, totalFee);
    }

    /** E3：病案首页归档（费用分类汇总 + 住院天数） */
    private void archiveHome(Admission admission, DischargeSummary summary) {
        MedicalRecordHome home = new MedicalRecordHome();
        home.setAdmissionId(admission.getId());
        home.setPatientId(admission.getPatientId());
        home.setDepartmentId(admission.getDepartmentId());
        home.setDoctorId(admission.getAttendingDoctorId());
        home.setAdmissionTime(admission.getAdmissionTime());
        home.setDischargeTime(summary.getDischargeTime());
        Integer days = bedOccupancyMapper.daysOccupied(admission.getId(), summary.getDischargeTime());
        home.setHospitalDays(days == null || days < 1 ? 1 : days);
        home.setDischargeDiag(summary.getDischargeDiag());
        home.setFeeBed(InpatientSupport.nz(homeMapper.sumFeeByType(admission.getId(), "BED")));
        home.setFeeDrug(InpatientSupport.nz(homeMapper.sumFeeByType(admission.getId(), "DRUG")));
        home.setFeeExam(InpatientSupport.nz(homeMapper.sumFeeByType(admission.getId(), "EXAM")));
        home.setFeeLab(InpatientSupport.nz(homeMapper.sumFeeByType(admission.getId(), "LAB")));
        BigDecimal totalFee = InpatientSupport.nz(feeMapper.sumByAdmission(admission.getId()));
        home.setFeeOther(totalFee
                .subtract(home.getFeeBed()).subtract(home.getFeeDrug())
                .subtract(home.getFeeExam()).subtract(home.getFeeLab())
                .max(BigDecimal.ZERO));
        home.setFeeTotal(totalFee);
        home.setSettlementAmount(summary.getSettlementAmount());
        homeMapper.insert(home);
        log.info("[住院病案首页] 归档: admissionId={}, days={}, totalFee={}",
                admission.getId(), home.getHospitalDays(), totalFee);
    }

    /** E4：出院随访衔接（clinic-service，失败仅告警） */
    private void linkFollowUp(Admission admission, Long doctorUserId) {
        try {
            CreateFollowUpDTO dto = new CreateFollowUpDTO();
            dto.setPatientId(admission.getPatientId());
            dto.setDoctorUserId(doctorUserId);
            dto.setFollowDate(LocalDate.now().plusDays(3));
            dto.setFollowMethod("PHONE");
            dto.setTemplate("出院随访：出院诊断 " + admission.getAdmissionDiag());
            followUpFeignClient.createFollowUp(dto);
        } catch (Exception e) {
            log.warn("[住院出院] 随访计划创建失败（忽略）: admissionId={}, {}",
                    admission.getId(), e.getMessage());
        }
    }

    /** 病案首页查询（E3） */
    public MedicalRecordHomeVO home(Long admissionId) {
        Admission admission = requireAdmission(admissionId);
        MedicalRecordHome home = homeMapper.selectByAdmission(admissionId);
        if (home == null) {
            return null;
        }
        MedicalRecordHomeVO vo = new MedicalRecordHomeVO();
        vo.setId(home.getId());
        vo.setAdmissionId(home.getAdmissionId());
        vo.setAdmissionNo(admission.getAdmissionNo());
        vo.setPatientId(home.getPatientId());
        vo.setDepartmentId(home.getDepartmentId());
        vo.setDoctorId(home.getDoctorId());
        vo.setAdmissionTime(home.getAdmissionTime());
        vo.setDischargeTime(home.getDischargeTime());
        vo.setHospitalDays(home.getHospitalDays());
        vo.setDischargeDiag(home.getDischargeDiag());
        vo.setMainOperation(home.getMainOperation());
        vo.setFeeBed(home.getFeeBed());
        vo.setFeeDrug(home.getFeeDrug());
        vo.setFeeExam(home.getFeeExam());
        vo.setFeeLab(home.getFeeLab());
        vo.setFeeOther(home.getFeeOther());
        vo.setFeeTotal(home.getFeeTotal());
        vo.setSettlementAmount(home.getSettlementAmount());
        return vo;
    }

    /** 重新结算（补缴/退款后刷新「多退少补」金额） */
    @Transactional(rollbackFor = Exception.class)
    public DischargeSummaryVO resettle(Long admissionId) {
        Admission admission = requireAdmission(admissionId);
        DischargeSummary summary = dischargeSummaryMapper.selectByAdmission(admissionId);
        if (summary == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "出院小结不存在，请先办理出院");
        }
        BigDecimal totalFee = InpatientSupport.nz(feeMapper.sumByAdmission(admissionId));
        BigDecimal depositBalance = InpatientSupport.nz(depositMapper.sumByAdmission(admissionId));
        dischargeSummaryMapper.updateSettlement(admissionId,
                totalFee.subtract(depositBalance), depositBalance);
        summary = dischargeSummaryMapper.selectByAdmission(admissionId);
        return toVO(summary, admission, totalFee);
    }

    /** 出院小结查询（医生/患者本人） */
    public DischargeSummaryVO summary(Long admissionId) {
        Admission admission = requireAdmission(admissionId);
        DischargeSummary summary = dischargeSummaryMapper.selectByAdmission(admissionId);
        if (summary == null) {
            return null;
        }
        return toVO(summary, admission, InpatientSupport.nz(feeMapper.sumByAdmission(admissionId)));
    }

    private Admission requireAdmission(Long admissionId) {
        Admission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        support.checkReadable(admission);
        return admission;
    }

    private DischargeSummaryVO toVO(DischargeSummary s, Admission a, BigDecimal totalFee) {
        DischargeSummaryVO vo = new DischargeSummaryVO();
        vo.setId(s.getId());
        vo.setAdmissionId(s.getAdmissionId());
        vo.setAdmissionNo(a.getAdmissionNo());
        vo.setPatientId(a.getPatientId());
        vo.setAdmissionDiag(s.getAdmissionDiag());
        vo.setDischargeDiag(s.getDischargeDiag());
        vo.setTreatmentProcess(s.getTreatmentProcess());
        vo.setDischargeCondition(s.getDischargeCondition());
        vo.setDischargeAdvice(s.getDischargeAdvice());
        vo.setDoctorId(s.getDoctorId());
        vo.setSettlementAmount(s.getSettlementAmount());
        vo.setDepositBalance(s.getDepositBalance());
        vo.setTotalFee(totalFee);
        vo.setDischargeTime(s.getDischargeTime());
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }
}
