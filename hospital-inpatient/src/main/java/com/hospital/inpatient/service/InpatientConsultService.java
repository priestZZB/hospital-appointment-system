package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.ConsultCreateDTO;
import com.hospital.inpatient.dto.ConsultHandleDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.InpatientConsult;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.InpatientConsultMapper;
import com.hospital.inpatient.vo.ConsultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 住院院内会诊服务（E1）：住院医生发起 → 受邀科室处理（接受/完成/拒绝）。
 * 状态机 PENDING -> ACCEPTED -> COMPLETED / REJECTED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientConsultService {

    private static final Set<String> ACTIONS = Set.of("ACCEPTED", "COMPLETED", "REJECTED");

    private final InpatientConsultMapper consultMapper;
    private final AdmissionMapper admissionMapper;
    private final InpatientSupport support;

    /** 发起会诊 */
    @Transactional(rollbackFor = Exception.class)
    public ConsultVO create(ConsultCreateDTO dto, Long requestDoctorUserId) {
        Admission admission = admissionMapper.selectById(dto.getAdmissionId());
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        if (!"ADMITTED".equals(admission.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已出院，不可发起会诊");
        }
        InpatientConsult consult = new InpatientConsult();
        consult.setAdmissionId(admission.getId());
        consult.setPatientId(admission.getPatientId());
        consult.setRequestDeptId(admission.getDepartmentId());
        consult.setRequestDoctorId(requestDoctorUserId);
        consult.setTargetDeptId(dto.getTargetDeptId());
        consult.setTargetDoctorId(dto.getTargetDoctorId());
        consult.setReason(dto.getReason());
        consultMapper.insert(consult);
        log.info("[住院会诊] 发起: id={}, admissionId={}, targetDept={}",
                consult.getId(), admission.getId(), dto.getTargetDeptId());
        return toVO(consultMapper.selectById(consult.getId()));
    }

    /** 处理会诊（受邀方） */
    @Transactional(rollbackFor = Exception.class)
    public ConsultVO handle(ConsultHandleDTO dto, Long handleDoctorUserId) {
        if (!ACTIONS.contains(dto.getAction())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "处理动作仅支持 ACCEPTED/COMPLETED/REJECTED");
        }
        InpatientConsult consult = consultMapper.selectById(dto.getConsultId());
        if (consult == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "会诊单不存在");
        }
        if (consultMapper.handle(dto.getConsultId(), dto.getAction(), dto.getOpinion(), handleDoctorUserId) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "会诊单已处理或状态已变更");
        }
        log.info("[住院会诊] 处理: id={}, action={}, doctorId={}", dto.getConsultId(), dto.getAction(), handleDoctorUserId);
        return toVO(consultMapper.selectById(dto.getConsultId()));
    }

    /** 会诊列表（按住院记录或受邀科室） */
    public List<ConsultVO> list(Long admissionId, Long targetDeptId, String status) {
        return consultMapper.selectList(admissionId, targetDeptId, status).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    private ConsultVO toVO(InpatientConsult c) {
        ConsultVO vo = new ConsultVO();
        vo.setId(c.getId());
        vo.setAdmissionId(c.getAdmissionId());
        Admission admission = admissionMapper.selectById(c.getAdmissionId());
        vo.setAdmissionNo(admission == null ? null : admission.getAdmissionNo());
        vo.setPatientId(c.getPatientId());
        vo.setRequestDeptId(c.getRequestDeptId());
        vo.setRequestDoctorId(c.getRequestDoctorId());
        vo.setTargetDeptId(c.getTargetDeptId());
        vo.setTargetDoctorId(c.getTargetDoctorId());
        vo.setReason(c.getReason());
        vo.setOpinion(c.getOpinion());
        vo.setStatus(c.getStatus());
        vo.setHandleDoctorId(c.getHandleDoctorId());
        vo.setHandleTime(c.getHandleTime());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }
}
