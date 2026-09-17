package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 出院小结 VO */
@Data
public class DischargeSummaryVO {
    private Long id;
    private Long admissionId;
    private String admissionNo;
    private Long patientId;
    private String admissionDiag;
    private String dischargeDiag;
    private String treatmentProcess;
    private String dischargeCondition;
    private String dischargeAdvice;
    private Long doctorId;
    /** 结算金额 = 总费用 - 预交金余额：正数应补缴，负数应退还 */
    private BigDecimal settlementAmount;
    private BigDecimal depositBalance;
    private BigDecimal totalFee;
    private LocalDateTime dischargeTime;
    private LocalDateTime createTime;
}
