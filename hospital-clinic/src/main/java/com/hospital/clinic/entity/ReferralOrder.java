package com.hospital.clinic.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 转诊单实体 */
@Data
public class ReferralOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String referralNo;
    private Long medicalRecordId;
    private Long patientId;
    private Long fromDeptId;
    private Long fromDoctorId;
    private Long toDeptId;
    private String reason;
    /** PENDING-待接收 / ACCEPTED-已接收 / COMPLETED-已完成 / REJECTED-已退回 */
    private String status;
    private LocalDateTime acceptTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
