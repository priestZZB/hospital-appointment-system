package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 生命体征 VO */
@Data
public class VitalSignVO {
    private Long id;
    private Long admissionId;
    private BigDecimal temperature;
    private Integer pulse;
    private Integer respiration;
    private String bloodPressure;
    private Integer bloodOxygen;
    private LocalDateTime recordTime;
}
