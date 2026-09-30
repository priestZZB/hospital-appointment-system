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

    /** 排班审批状态：PENDING-待门诊部确认 / CONFIRMED-已确认（可挂号）/ REJECTED-已驳回 */
    private String auditStatus;

    /** 号别：NORMAL-普通号 / EXPERT-专家号（按医生职称档位价收费，迭代9 A5） */
    private String feeType;

    /** 排班维度加号开关：0-禁止加号 / 1-允许加号超挂（迭代9 A2） */
    private Integer overbook;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
