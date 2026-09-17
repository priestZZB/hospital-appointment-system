package com.hospital.clinic.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 转诊单 VO（含医生/科室名称） */
@Data
public class ReferralOrderVO {
    private Long id;
    private String referralNo;
    private Long medicalRecordId;
    private Long patientId;
    private Long fromDeptId;
    private String fromDeptName;
    private Long fromDoctorId;
    private String fromDoctorName;
    private Long toDeptId;
    private String toDeptName;
    private String reason;
    private String status;
    private LocalDateTime acceptTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
