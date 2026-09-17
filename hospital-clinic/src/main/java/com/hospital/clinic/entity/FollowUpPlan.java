package com.hospital.clinic.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 随访计划实体 */
@Data
public class FollowUpPlan implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long patientId;
    private Long medicalRecordId;
    private Long doctorId;
    private LocalDate followDate;
    /** PHONE-电话 / VISIT-门诊复诊 / WECHAT-微信 / OTHER-其他 */
    private String followMethod;
    private String template;
    /** PENDING-待随访 / DONE-已完成 / OVERDUE-已逾期 / CANCELLED-已取消 */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
