package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 出院小结实体 */
@Data
public class DischargeSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private String admissionDiag;
    private String dischargeDiag;
    private String treatmentProcess;
    private String dischargeCondition;
    private String dischargeAdvice;
    private Long doctorId;
    private BigDecimal settlementAmount;
    private BigDecimal depositBalance;
    private LocalDateTime dischargeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
