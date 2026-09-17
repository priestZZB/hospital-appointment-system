package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 预交金缴纳 DTO */
@Data
public class DepositPayDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
    private String payMethod;
}
