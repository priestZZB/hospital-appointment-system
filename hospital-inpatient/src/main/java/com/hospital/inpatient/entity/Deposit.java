package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预交金流水实体 */
@Data
public class Deposit implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private BigDecimal amount;
    private String payMethod;
    private BigDecimal balanceAfter;
    private Long operatorId;
    private LocalDateTime createTime;
}
