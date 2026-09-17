package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 床位实体 */
@Data
public class Bed implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long departmentId;
    private String roomNo;
    private String bedNo;
    /** NORMAL / ICU / ISOLATION */
    private String bedType;
    private BigDecimal dailyFee;
    /** AVAILABLE / OCCUPIED / MAINTENANCE */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
