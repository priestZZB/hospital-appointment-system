package com.hospital.patient.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 过敏史表实体
 */
@Data
public class Allergy implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;
    private String allergen;
    private String reactionType;
    private String severity;
    /** 来源：PATIENT-患者自填 / DOCTOR-医生录入 */
    private String source;
    private Long sourceDoctorId;
    private LocalDateTime createTime;
}
