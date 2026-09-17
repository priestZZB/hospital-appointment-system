package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 住院医嘱实体 */
@Data
public class InpatientMedicalOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String orderNo;
    private Long admissionId;
    private Long doctorId;
    /** LONG_TERM / TEMPORARY */
    private String orderType;
    /** DRUG / EXAM / LAB / INFUSION / NURSING / DIET / OTHER */
    private String category;
    private String content;
    private String frequency;
    /** OPEN / CONFIRMED / EXECUTING / COMPLETED / STOPPED */
    private String status;
    private LocalDateTime openTime;
    private LocalDateTime confirmTime;
    private Long confirmNurseId;
    private LocalDateTime stopTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
