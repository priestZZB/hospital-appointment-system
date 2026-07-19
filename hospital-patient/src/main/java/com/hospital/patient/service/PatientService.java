package com.hospital.patient.service;

import com.hospital.patient.dto.UpdatePatientDTO;
import com.hospital.patient.entity.Patient;
import com.hospital.patient.entity.VisitCard;
import com.hospital.patient.mapper.PatientMapper;
import com.hospital.patient.mapper.VisitCardMapper;
import com.hospital.patient.vo.PatientVO;
import com.hospital.patient.vo.VisitCardVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 患者档案服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientMapper patientMapper;
    private final VisitCardMapper visitCardMapper;

    /**
     * 查询患者档案（含就诊卡列表）
     */
    public PatientVO getProfile(Long userId) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }
        return toVO(patient);
    }

    /**
     * 编辑患者档案（仅允许编辑非关键字段）
     */
    @Transactional(rollbackFor = Exception.class)
    public PatientVO updateProfile(Long userId, UpdatePatientDTO dto) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }

        if (dto.getName() != null) patient.setName(dto.getName());
        if (dto.getGender() != null) patient.setGender(dto.getGender());
        if (dto.getBirthDate() != null) patient.setBirthDate(dto.getBirthDate());
        if (dto.getIdCard() != null) patient.setIdCard(dto.getIdCard());
        if (dto.getEmergencyContact() != null) patient.setEmergencyContact(dto.getEmergencyContact());
        if (dto.getEmergencyPhone() != null) patient.setEmergencyPhone(dto.getEmergencyPhone());

        patientMapper.update(patient);
        log.info("[患者] 档案更新: userId={}", userId);
        return toVO(patientMapper.selectByUserId(userId));
    }

    /**
     * 查询就诊卡列表
     */
    public List<VisitCardVO> getVisitCards(Long userId) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }
        List<VisitCard> cards = visitCardMapper.selectByPatientId(patient.getId());
        return cards.stream().map(this::toVisitCardVO).collect(Collectors.toList());
    }

    // ==================== 内部方法（供 Feign / 其他 Service 调用） ====================

    /**
     * 根据 userId 查询（内部调用，返回实体）
     */
    public Patient getByUserId(Long userId) {
        return patientMapper.selectByUserId(userId);
    }

    /**
     * 根据 patientId 查询（内部调用，返回实体）
     */
    public Patient getById(Long patientId) {
        return patientMapper.selectById(patientId);
    }

    // ==================== 实体 → VO 转换 ====================

    private PatientVO toVO(Patient p) {
        return PatientVO.builder()
                .id(p.getId()).userId(p.getUserId()).name(p.getName())
                .gender(p.getGender()).birthDate(p.getBirthDate()).idCard(p.getIdCard())
                .phone(p.getPhone()).emergencyContact(p.getEmergencyContact())
                .emergencyPhone(p.getEmergencyPhone()).verifyStatus(p.getVerifyStatus())
                .idCardFrontUrl(p.getIdCardFrontUrl()).idCardBackUrl(p.getIdCardBackUrl())
                .verifyComment(p.getVerifyComment()).avatarUrl(p.getAvatarUrl())
                .createTime(p.getCreateTime())
                .build();
    }

    private VisitCardVO toVisitCardVO(VisitCard c) {
        return VisitCardVO.builder()
                .id(c.getId()).cardNo(c.getCardNo())
                .status(c.getStatus()).issueDate(c.getIssueDate())
                .build();
    }
}
