package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 生命体征录入 DTO */
@Data
public class VitalSignDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    private BigDecimal temperature;
    private Integer pulse;
    private Integer respiration;
    private String bloodPressure;
    private Integer bloodOxygen;
}
