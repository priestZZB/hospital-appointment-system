package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 排班创建 DTO
 */
@Data
public class ScheduleCreateDTO {

    @NotNull(message = "医生不能为空")
    private Long doctorId;

    @NotNull(message = "科室不能为空")
    private Long departmentId;

    @NotNull(message = "出诊日期不能为空")
    private LocalDate scheduleDate;

    @NotNull(message = "时段不能为空")
    private String period;

    @NotNull(message = "开始时间不能为空")
    private LocalTime periodStart;

    @NotNull(message = "结束时间不能为空")
    private LocalTime periodEnd;

    /** 号源总数（可选，默认按 slotDuration 自动计算） */
    private Integer totalSlots;

    /** 单次预约时长（分钟），默认 10 */
    private Integer slotDuration;

    /** 挂号费 */
    private BigDecimal registerFee;
}
