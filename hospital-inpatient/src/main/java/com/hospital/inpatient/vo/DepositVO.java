package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预交金流水 VO */
@Data
public class DepositVO {
    private Long id;
    private Long admissionId;
    private BigDecimal amount;
    private String payMethod;
    private BigDecimal balanceAfter;
    private Long operatorId;
    private LocalDateTime createTime;
}
