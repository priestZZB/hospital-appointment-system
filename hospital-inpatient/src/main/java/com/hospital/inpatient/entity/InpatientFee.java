package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 住院费用流水实体 */
@Data
public class InpatientFee implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    /** BED / TREATMENT / DRUG / EXAM / LAB / NURSING / OTHER */
    private String feeType;
    private String itemName;
    private BigDecimal amount;
    private LocalDate billDate;
    private LocalDateTime createTime;
}
