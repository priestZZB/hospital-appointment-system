package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 号源信息 VO
 */
@Data
@Builder
public class SlotVO {

    private Long id;
    private Long scheduleId;
    private Long doctorId;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private LocalDate scheduleDate;
    private String period;
    private Integer slotSeq;
    private LocalTime slotStart;
    private LocalTime slotEnd;
    private String status;
    private BigDecimal registerFee;
}
