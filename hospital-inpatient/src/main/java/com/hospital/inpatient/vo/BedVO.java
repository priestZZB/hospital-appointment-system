package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 床位 VO */
@Data
public class BedVO {
    private Long id;
    private Long departmentId;
    private String roomNo;
    private String bedNo;
    private String bedType;
    private BigDecimal dailyFee;
    private String status;
    /** 当前占用者（住院号） */
    private String occupantAdmissionNo;
    private Long occupantPatientId;
}
