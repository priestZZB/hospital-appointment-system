package com.hospital.clinic.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 医疗证明实体 */
@Data
public class MedicalCertificate implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String certNo;
    /** DIAGNOSIS-诊断证明 / SICK_LEAVE-病假条 / REFERRAL-转诊单 / MEDICAL_ADVICE-医疗建议 */
    private String certType;
    private Long patientId;
    private Long doctorId;
    private Long medicalRecordId;
    private String content;
    private Integer days;
    private LocalDate startDate;
    /** ISSUED-已开具 / CANCELLED-已作废 */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
