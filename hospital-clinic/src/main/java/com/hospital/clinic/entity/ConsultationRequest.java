package com.hospital.clinic.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 会诊请求表实体 */
@Data
public class ConsultationRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String requestNo;
    private Long medicalRecordId;
    private Long patientId;
    private Long applyDeptId;
    private Long applyDoctorId;
    private Long targetDeptId;
    private Long targetDoctorId;
    private String reason;
    /** PENDING-待会诊 / ACCEPTED-已接受 / COMPLETED-已完成 / REJECTED-已拒绝 */
    private String status;
    private String consultOpinion;
    private Long consultDoctorId;
    private LocalDateTime consultTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
