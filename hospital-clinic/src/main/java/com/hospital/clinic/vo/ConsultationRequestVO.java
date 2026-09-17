package com.hospital.clinic.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 会诊请求 VO（含医生/科室名称） */
@Data
public class ConsultationRequestVO {
    private Long id;
    private String requestNo;
    private Long medicalRecordId;
    private Long patientId;
    private Long applyDeptId;
    private String applyDeptName;
    private Long applyDoctorId;
    private String applyDoctorName;
    private Long targetDeptId;
    private String targetDeptName;
    private Long targetDoctorId;
    private String targetDoctorName;
    private String reason;
    private String status;
    private String consultOpinion;
    private Long consultDoctorId;
    private String consultDoctorName;
    private LocalDateTime consultTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
