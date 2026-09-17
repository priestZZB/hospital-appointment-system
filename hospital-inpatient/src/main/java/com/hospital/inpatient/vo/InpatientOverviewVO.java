package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院总览 VO（护士站/医生站看板） */
@Data
public class InpatientOverviewVO {
    private Long admissionId;
    private String admissionNo;
    private String patientName;
    private String roomNo;
    private String bedNo;
    private String status;
    private long pendingOrderCount;
    private long executingOrderCount;
    private BigDecimal depositBalance;
    private BigDecimal totalFee;
    private VitalSignVO latestVital;
}
