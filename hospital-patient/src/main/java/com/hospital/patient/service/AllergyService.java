package com.hospital.patient.service;

import com.hospital.patient.dto.AllergyDTO;
import com.hospital.patient.entity.Allergy;
import com.hospital.patient.entity.Patient;
import com.hospital.patient.mapper.AllergyMapper;
import com.hospital.patient.mapper.PatientMapper;
import com.hospital.patient.vo.AllergyVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 过敏史服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllergyService {

    private final AllergyMapper allergyMapper;
    private final PatientMapper patientMapper;

    /**
     * 查询过敏史列表
     */
    public List<AllergyVO> listByUserId(Long userId) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }
        return allergyMapper.selectByPatientId(patient.getId()).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 新增过敏史
     */
    @Transactional(rollbackFor = Exception.class)
    public AllergyVO add(Long userId, AllergyDTO dto) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }

        Allergy allergy = new Allergy();
        allergy.setPatientId(patient.getId());
        allergy.setAllergen(dto.getAllergen());
        allergy.setReactionType(dto.getReactionType());
        allergy.setSeverity(dto.getSeverity());
        allergy.setSource("PATIENT");
        allergyMapper.insert(allergy);

        log.info("[过敏史] 新增: patientId={}, allergen={}", patient.getId(), dto.getAllergen());
        return toVO(allergy);
    }

    /**
     * 删除过敏史（患者只能删除自己填写的记录）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long allergyId) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }
        allergyMapper.deleteById(allergyId, patient.getId());
        log.info("[过敏史] 删除: allergyId={}, patientId={}", allergyId, patient.getId());
    }

    // ==================== 内部方法 ====================

    public List<AllergyVO> listByPatientId(Long patientId) {
        return allergyMapper.selectByPatientId(patientId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    private AllergyVO toVO(Allergy a) {
        return AllergyVO.builder()
                .id(a.getId()).allergen(a.getAllergen())
                .reactionType(a.getReactionType()).severity(a.getSeverity())
                .source(a.getSource()).createTime(a.getCreateTime())
                .build();
    }
}
