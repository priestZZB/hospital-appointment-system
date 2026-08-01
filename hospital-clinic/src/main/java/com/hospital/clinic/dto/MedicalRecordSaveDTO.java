package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 病历保存请求 DTO
 */
@Data
public class MedicalRecordSaveDTO {

    /** 预约ID（新建病历时传入） */
    private Long appointmentId;

    /** 主诉 */
    private String chiefComplaint;

    /** 现病史 */
    private String presentIllness;

    /** 既往史 */
    private String pastHistory;

    /** 体温 */
    private BigDecimal temperature;

    /** 脉搏 */
    private Integer pulse;

    /** 呼吸 */
    private Integer respiration;

    /** 血压 */
    private String bloodPressure;

    /** 诊断编码 */
    private String diagnosisCode;

    /** 诊断描述（提交时必填） */
    private String diagnosisDesc;

    /** 处理意见 */
    private String treatmentOpinion;

    /** 转诊科室ID */
    private Long referralDeptId;

    /** 转诊原因 */
    private String referralReason;

    /** 操作类型：DRAFT-保存草稿 / SUBMIT-提交 */
    @NotBlank(message = "操作类型不能为空")
    private String action;
}
