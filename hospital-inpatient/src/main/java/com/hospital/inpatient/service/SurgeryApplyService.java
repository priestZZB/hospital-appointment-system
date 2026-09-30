package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.SurgeryApplyDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.Surgery;
import com.hospital.inpatient.entity.SurgeryApply;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.SurgeryApplyMapper;
import com.hospital.inpatient.vo.SurgeryApplyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 手术申请服务（E6）：住院医生开手术申请单。
 * 迭代10 F2：排台成功后同步生成统一手术单（source=INPATIENT，apply_id 关联，同事务整体回滚），
 * 返回 VO 新增 surgeryId 字段（向后兼容）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeryApplyService {

    private final SurgeryApplyMapper surgeryApplyMapper;
    private final AdmissionMapper admissionMapper;
    private final SurgeryService surgeryService;
    private final InpatientSupport support;

    /** 开手术申请单 */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryApplyVO apply(SurgeryApplyDTO dto, Long applyDoctorUserId) {
        Admission admission = admissionMapper.selectById(dto.getAdmissionId());
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        if (!"ADMITTED".equals(admission.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已出院，不可开手术申请");
        }
        SurgeryApply apply = new SurgeryApply();
        apply.setAdmissionId(admission.getId());
        apply.setPatientId(admission.getPatientId());
        apply.setSurgeryName(dto.getSurgeryName());
        apply.setAnesthesiaType(dto.getAnesthesiaType());
        apply.setApplyDoctorId(applyDoctorUserId);
        apply.setRemark(dto.getRemark());
        surgeryApplyMapper.insert(apply);
        log.info("[手术申请] 开单: id={}, admissionId={}, surgery={}",
                apply.getId(), admission.getId(), dto.getSurgeryName());
        return toVO(surgeryApplyMapper.selectById(apply.getId()));
    }

    /**
     * 排台（迭代10 F2 增强）：申请单 PENDING → SCHEDULED 后，
     * 同事务内同步创建统一手术单（source=INPATIENT、apply_id 关联）；
     * surgery 表插入失败抛异常 → 本事务整体回滚（申请单排台一并回滚）。
     * 原方法签名与返回结构不变，仅 VO 新增 surgeryId。
     */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryApplyVO schedule(Long applyId, LocalDateTime scheduledTime, String operatingRoom) {
        requireApply(applyId);
        if (surgeryApplyMapper.schedule(applyId, scheduledTime, operatingRoom) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "申请单已排台或已取消");
        }
        SurgeryApply apply = surgeryApplyMapper.selectById(applyId);
        // F2 打通：排台成功即生成统一手术单（同事务，失败整体回滚；已存在时幂等返回）
        Surgery surgery = surgeryService.createFromApply(apply);
        SurgeryApplyVO vo = toVO(apply);
        vo.setSurgeryId(surgery.getId());
        return vo;
    }

    /**
     * 取消申请：若已生成统一手术单且仍在 APPLIED/SCHEDULED，同步撤台（同事务），
     * 避免「申请单已取消、手术间仍留台」的状态不一致；手术已开始/完成时不强撤（记 warning）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryApplyVO cancel(Long applyId) {
        requireApply(applyId);
        if (surgeryApplyMapper.cancel(applyId) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "申请单已结束");
        }
        Surgery surgery = surgeryService.syncCancelFromApply(applyId);
        SurgeryApplyVO vo = toVO(surgeryApplyMapper.selectById(applyId));
        if (surgery != null) {
            vo.setSurgeryId(surgery.getId());
        }
        return vo;
    }

    /** 申请单列表 */
    public List<SurgeryApplyVO> list(Long admissionId, String status) {
        return surgeryApplyMapper.selectList(admissionId, status).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    private void requireApply(Long applyId) {
        if (surgeryApplyMapper.selectById(applyId) == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "手术申请单不存在");
        }
    }

    private SurgeryApplyVO toVO(SurgeryApply s) {
        SurgeryApplyVO vo = new SurgeryApplyVO();
        vo.setId(s.getId());
        vo.setAdmissionId(s.getAdmissionId());
        Admission admission = admissionMapper.selectById(s.getAdmissionId());
        vo.setAdmissionNo(admission == null ? null : admission.getAdmissionNo());
        vo.setPatientId(s.getPatientId());
        vo.setSurgeryName(s.getSurgeryName());
        vo.setAnesthesiaType(s.getAnesthesiaType());
        vo.setApplyDoctorId(s.getApplyDoctorId());
        vo.setScheduledTime(s.getScheduledTime());
        vo.setOperatingRoom(s.getOperatingRoom());
        vo.setStatus(s.getStatus());
        vo.setRemark(s.getRemark());
        vo.setCreateTime(s.getCreateTime());
        // 迭代10：已排台/已完成的申请单回查关联统一手术单（按状态过滤，避免全量回查）
        if ("SCHEDULED".equals(s.getStatus()) || "COMPLETED".equals(s.getStatus())) {
            Surgery surgery = surgeryService.findByApplyId(s.getId());
            vo.setSurgeryId(surgery == null ? null : surgery.getId());
        }
        return vo;
    }
}
