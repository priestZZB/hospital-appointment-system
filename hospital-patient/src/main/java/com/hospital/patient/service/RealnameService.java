package com.hospital.patient.service;

import com.hospital.patient.dto.RealnameReviewDTO;
import com.hospital.patient.dto.RealnameSubmitDTO;
import com.hospital.patient.entity.Patient;
import com.hospital.patient.mapper.PatientMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实名认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealnameService {

    private final PatientMapper patientMapper;

    /**
     * 提交实名认证
     * <p>
     * 上传身份证照片后调用，将认证状态改为"审核中"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, RealnameSubmitDTO dto) {
        Patient patient = patientMapper.selectByUserId(userId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }
        if (patient.getVerifyStatus() != null && (patient.getVerifyStatus() == 1 || patient.getVerifyStatus() == 2)) {
            String msg = patient.getVerifyStatus() == 1 ? "实名认证正在审核中，请勿重复提交" : "已通过实名认证，无需重复提交";
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, msg);
        }

        Patient update = new Patient();
        update.setId(patient.getId());
        update.setName(dto.getName());
        update.setIdCard(dto.getIdCard());
        update.setIdCardFrontUrl(dto.getIdCardFrontUrl());
        update.setIdCardBackUrl(dto.getIdCardBackUrl());
        update.setVerifyStatus(1); // 审核中
        patientMapper.update(update);

        log.info("[实名认证] 提交成功: userId={}", userId);
    }

    /**
     * 管理员审核实名认证
     * <p>
     * verifyStatus: 2-通过, 3-驳回
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long patientId, RealnameReviewDTO dto) {
        if (dto.getVerifyStatus() != 2 && dto.getVerifyStatus() != 3) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核结果值非法，仅允许 2(通过) 或 3(驳回)");
        }
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "患者档案不存在");
        }
        if (patient.getVerifyStatus() == null || patient.getVerifyStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "当前状态不允许审核，仅审核中状态可审核");
        }

        Patient update = new Patient();
        update.setId(patient.getId());
        update.setVerifyStatus(dto.getVerifyStatus());
        update.setVerifyComment(dto.getVerifyComment());
        patientMapper.update(update);

        log.info("[实名认证] 审核完成: patientId={}, result={}", patientId, dto.getVerifyStatus());
    }
}
