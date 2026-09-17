package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 生命体征实体 */
@Data
public class VitalSign implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private BigDecimal temperature;
    private Integer pulse;
    private Integer respiration;
    private String bloodPressure;
    private Integer bloodOxygen;
    private LocalDateTime recordTime;
    private Long operatorId;
    private LocalDateTime createTime;
}
