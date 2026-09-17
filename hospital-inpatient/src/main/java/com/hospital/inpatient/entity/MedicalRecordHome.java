package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院病案首页实体（出院归档） */
@Data
public class MedicalRecordHome implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private Long patientId;
    private Long departmentId;
    private Long doctorId;
    private LocalDateTime admissionTime;
    private LocalDateTime dischargeTime;
    private Integer hospitalDays;
    private String dischargeDiag;
    private String mainOperation;
    private BigDecimal feeBed;
    private BigDecimal feeDrug;
    private BigDecimal feeExam;
    private BigDecimal feeLab;
    private BigDecimal feeOther;
    private BigDecimal feeTotal;
    private BigDecimal settlementAmount;
    private LocalDateTime createTime;
}
