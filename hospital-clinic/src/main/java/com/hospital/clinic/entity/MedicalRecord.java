package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 病历表实体
 *
 * @see V1__init.sql — medical_record 表 DDL
 */
@Data
public class MedicalRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 appointment.id */
    private Long appointmentId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 关联 doctor.id */
    private Long doctorId;

    /** 科室ID（冗余） */
    private Long departmentId;

    /** 主诉 */
    private String chiefComplaint;

    /** 现病史 */
    private String presentIllness;

    /** 既往史 */
    private String pastHistory;

    /** 体温（℃） */
    private BigDecimal temperature;

    /** 脉搏（次/分） */
    private Integer pulse;

    /** 呼吸（次/分） */
    private Integer respiration;

    /** 血压（如 120/80） */
    private String bloodPressure;

    /** 初步诊断 ICD-10 编码 */
    private String diagnosisCode;

    /** 诊断描述 */
    private String diagnosisDesc;

    /** 处理意见 */
    private String treatmentOpinion;

    /** 转诊目标科室ID */
    private Long referralDeptId;

    /** 转诊原因 */
    private String referralReason;

    /** DRAFT-草稿 / SUBMITTED-已提交 / COMPLETED-已完成 */
    private String status;

    /** 是否回诊病历 */
    private Integer isReturnVisit;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
