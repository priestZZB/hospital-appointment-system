package com.hospital.clinic.service;

import cn.hutool.core.lang.UUID;
import com.hospital.clinic.entity.ConsultationRequest;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.MedicalRecord;
import com.hospital.clinic.entity.ReferralOrder;
import com.hospital.clinic.mapper.ConsultationRequestMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.MedicalRecordMapper;
import com.hospital.clinic.mapper.ReferralOrderMapper;
import com.hospital.clinic.vo.ConsultationRequestVO;
import com.hospital.clinic.vo.ReferralOrderVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会诊 + 转诊协同服务
 * <p>
 * 会诊：医生发起会诊请求 → 目标科室/医生处理（接受/填写意见/完成/拒绝）
 * 转诊：医生创建转诊单 → 目标科室接收/完成/退回
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationCollaborationService {

    private final ConsultationRequestMapper consultationRequestMapper;
    private final ReferralOrderMapper referralOrderMapper;
    private final DoctorMapper doctorMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final PatientFeignClient patientFeignClient;

    // ==================== 会诊 ====================

    @Transactional(rollbackFor = Exception.class)
    public ConsultationRequestVO createConsultationRequest(Long userId, ConsultationRequest request) {
        Doctor doctor = requireDoctor(userId);
        if (request.getMedicalRecordId() == null || request.getTargetDeptId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "病历与目标科室不能为空");
        }
        MedicalRecord record = medicalRecordMapper.selectById(request.getMedicalRecordId());
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        if (!Objects.equals(record.getDoctorId(), doctor.getId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "只能为本人的病历发起会诊");
        }
        request.setRequestNo("CON" + UUID.fastUUID().toString().substring(0, 8).toUpperCase());
        request.setApplyDeptId(doctor.getDepartmentId());
        request.setApplyDoctorId(doctor.getId());
        request.setPatientId(record.getPatientId());
        request.setStatus("PENDING");
        consultationRequestMapper.insert(request);
        log.info("[会诊] 发起会诊: requestId={}, targetDept={}, targetDoctor={}",
                request.getId(), request.getTargetDeptId(), request.getTargetDoctorId());
        return getConsultationRequest(request.getId());
    }

    public ConsultationRequestVO getConsultationRequest(Long requestId) {
        ConsultationRequestVO vo = consultationRequestMapper.selectVOById(requestId);
        if (vo == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "会诊请求不存在");
        }
        return vo;
    }

    public List<ConsultationRequestVO> listConsultationRequests(Long userId, Long targetDeptId, Long targetDoctorId,
                                                                Long patientId, String status) {
        Long participantDoctorId = null;
        // 患者只查本人；医生查本人发起/目标；管理员全量
        if (UserContext.isPatient()) {
            patientId = resolvePatientId(userId);
        } else if (!UserContext.isAdminOrSuperAdmin()) {
            Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor != null) {
                participantDoctorId = doctor.getId();
            }
        }
        return consultationRequestMapper.selectList(targetDeptId, targetDoctorId, participantDoctorId, patientId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleConsultationRequest(Long userId, Long requestId, String action, String opinion) {
        ConsultationRequest request = consultationRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "会诊请求不存在");
        }
        if ("COMPLETED".equals(request.getStatus()) || "REJECTED".equals(request.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "会诊请求已处理");
        }
        Doctor doctor = doctorMapper.selectByUserId(userId);
        boolean isTarget = doctor != null && (request.getTargetDoctorId() == null
                || Objects.equals(request.getTargetDoctorId(), doctor.getId()));
        boolean isApply = doctor != null && Objects.equals(request.getApplyDoctorId(), doctor.getId());
        boolean admin = UserContext.isAdminOrSuperAdmin();
        if (!admin && !isTarget && !isApply) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权处理该会诊");
        }

        String targetStatus;
        switch (action == null ? "" : action) {
            case "ACCEPT" -> targetStatus = "ACCEPTED";
            case "COMPLETE" -> targetStatus = "COMPLETED";
            case "REJECT" -> targetStatus = "REJECTED";
            default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持的处理动作");
        }
        consultationRequestMapper.updateHandle(requestId, targetStatus, opinion,
                doctor != null ? doctor.getId() : null);
        log.info("[会诊] 处理会诊: requestId={}, action={}", requestId, action);
    }

    // ==================== 转诊 ====================

    @Transactional(rollbackFor = Exception.class)
    public ReferralOrderVO createReferralOrder(Long userId, ReferralOrder referral) {
        Doctor doctor = requireDoctor(userId);
        if (referral.getMedicalRecordId() == null || referral.getToDeptId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "病历与转入科室不能为空");
        }
        MedicalRecord record = medicalRecordMapper.selectById(referral.getMedicalRecordId());
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RECORD_NOT_FOUND);
        }
        if (!Objects.equals(record.getDoctorId(), doctor.getId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "只能为本人的病历发起转诊");
        }
        referral.setReferralNo("REF" + UUID.fastUUID().toString().substring(0, 8).toUpperCase());
        referral.setFromDeptId(doctor.getDepartmentId());
        referral.setFromDoctorId(doctor.getId());
        referral.setPatientId(record.getPatientId());
        referral.setStatus("PENDING");
        referralOrderMapper.insert(referral);
        log.info("[转诊] 创建转诊单: referralId={}, toDept={}", referral.getId(), referral.getToDeptId());
        return getReferralOrder(referral.getId());
    }

    public ReferralOrderVO getReferralOrder(Long referralId) {
        ReferralOrderVO vo = referralOrderMapper.selectVOById(referralId);
        if (vo == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "转诊单不存在");
        }
        return vo;
    }

    public List<ReferralOrderVO> listReferralOrders(Long userId, Long toDeptId, Long patientId, String status) {
        Long fromDoctorId = null;
        if (UserContext.isPatient()) {
            patientId = resolvePatientId(userId);
        } else if (!UserContext.isAdminOrSuperAdmin()) {
            Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor != null) {
                fromDoctorId = doctor.getId();
            }
        }
        return referralOrderMapper.selectList(fromDoctorId, toDeptId, patientId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleReferralOrder(Long userId, Long referralId, String action) {
        ReferralOrder referral = referralOrderMapper.selectById(referralId);
        if (referral == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "转诊单不存在");
        }
        if ("COMPLETED".equals(referral.getStatus()) || "REJECTED".equals(referral.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "转诊单已处理");
        }
        Doctor doctor = doctorMapper.selectByUserId(userId);
        boolean admin = UserContext.isAdminOrSuperAdmin();
        boolean toDeptDoctor = doctor != null && Objects.equals(doctor.getDepartmentId(), referral.getToDeptId());
        boolean fromDoctor = doctor != null && Objects.equals(doctor.getId(), referral.getFromDoctorId());
        if (!admin && !toDeptDoctor && !fromDoctor) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权处理该转诊单");
        }

        switch (action == null ? "" : action) {
            case "ACCEPT" -> {
                if (!admin && !toDeptDoctor) {
                    throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅转入科室医生可接收转诊");
                }
                referralOrderMapper.updateAccept(referralId, "ACCEPTED");
            }
            case "COMPLETE" -> referralOrderMapper.updateComplete(referralId);
            case "REJECT" -> {
                if (!fromDoctor && !admin) {
                    throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅转出医生可退回转诊");
                }
                referralOrderMapper.updateAccept(referralId, "REJECTED");
            }
            default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持的处理动作");
        }
        log.info("[转诊] 处理转诊单: referralId={}, action={}", referralId, action);
    }

    // ==================== 私有 ====================

    private Doctor requireDoctor(Long userId) {
        Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        return doctor;
    }

    /** 通过 userId 解析 patientId */
    private Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> info = patientFeignClient.getByUserId(userId);
            if (info == null || info.get("id") == null) {
                return null;
            }
            return Long.valueOf(info.get("id").toString());
        } catch (Exception e) {
            return null;
        }
    }
}
