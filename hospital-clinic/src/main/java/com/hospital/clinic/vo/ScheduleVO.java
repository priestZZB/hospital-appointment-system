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
    private String auditStatus;

    /** 号别：NORMAL-普通号 / EXPERT-专家号（迭代9 A5） */
    private String feeType;

    /** 排班维度加号开关：0-禁止 / 1-允许加号超挂（迭代9 A2） */
    private Integer overbook;

    private LocalDateTime createTime;
}
