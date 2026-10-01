package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 患者路径实体（迭代12 J1） */
@Data
public class PatientPath implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long templateId;
    private Long admissionId;
    private Long patientId;
    private LocalDateTime enterTime;
    private Integer currentDay;
    /** IN_PATH | VARIATION | EXITED | COMPLETED */
    private String status;
    private String variationReason;
    private LocalDateTime exitTime;
}
