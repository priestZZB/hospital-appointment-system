package com.hospital.clinic.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 医疗证明 VO（含医生姓名） */
@Data
public class MedicalCertificateVO {
    private Long id;
    private String certNo;
    private String certType;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private Long medicalRecordId;
    private String content;
    private Integer days;
    private LocalDate startDate;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
