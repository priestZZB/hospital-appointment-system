package com.hospital.clinic.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 随访计划 VO（含记录列表） */
@Data
public class FollowUpPlanVO {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long medicalRecordId;
    private Long doctorId;
    private String doctorName;
    private LocalDate followDate;
    private String followMethod;
    private String template;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private java.util.List<FollowUpRecordVO> records;
}
