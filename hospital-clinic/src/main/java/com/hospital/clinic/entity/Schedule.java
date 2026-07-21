package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 排班计划表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">schedule 表 DDL</a>
 */
@Data
public class Schedule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 doctor.id */
    private Long doctorId;

    /** 关联 department.id */
    private Long departmentId;

    /** 出诊日期 */
    private LocalDate scheduleDate;

    /** 时段：AM-上午 / PM-下午 */
    private String period;

    /** 时段开始时间 */
    private LocalTime periodStart;

    /** 时段结束时间 */
    private LocalTime periodEnd;

    /** 该时段号源总数 */
    private Integer totalSlots;

    /** 单次预约时长（分钟） */
    private Integer slotDuration;

    /** 挂号费 */
    private BigDecimal registerFee;

    /** 状态：1-正常 0-已取消 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
