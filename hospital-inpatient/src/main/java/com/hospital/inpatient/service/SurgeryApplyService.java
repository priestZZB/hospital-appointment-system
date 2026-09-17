package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.SurgeryApplyDTO;
import com.hospital.inpatient.entity.Admission;
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
 * 排台/术前评估/手麻记录在迭代10 手术麻醉模块扩展。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeryApplyService {

    private final SurgeryApplyMapper surgeryApplyMapper;
    private final AdmissionMapper admissionMapper;
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

    /** 排台（管理员/手术室，迭代10 完善） */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryApplyVO schedule(Long applyId, LocalDateTime scheduledTime, String operatingRoom) {
        requireApply(applyId);
        if (surgeryApplyMapper.schedule(applyId, scheduledTime, operatingRoom) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "申请单已排台或已取消");
        }
        return toVO(surgeryApplyMapper.selectById(applyId));
    }

    /** 取消申请 */
    @Transactional(rollbackFor = Exception.class)
    public SurgeryApplyVO cancel(Long applyId) {
        requireApply(applyId);
        if (surgeryApplyMapper.cancel(applyId) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "申请单已结束");
        }
        return toVO(surgeryApplyMapper.selectById(applyId));
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
        return vo;
    }
}
