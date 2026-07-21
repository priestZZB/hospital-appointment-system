package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 排班信息 VO
 */
@Data
@Builder
public class ScheduleVO {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private LocalDate scheduleDate;
    private String period;
    private LocalTime periodStart;
    private LocalTime periodEnd;
    private Integer totalSlots;
    private Integer availableSlots;
    private Integer slotDuration;
    private BigDecimal registerFee;
    private Integer status;
    private LocalDateTime createTime;
}
