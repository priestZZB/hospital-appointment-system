package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院病案首页 VO */
@Data
public class MedicalRecordHomeVO {
    private Long id;
    private Long admissionId;
    private String admissionNo;
    private Long patientId;
    private String patientName;
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
}
