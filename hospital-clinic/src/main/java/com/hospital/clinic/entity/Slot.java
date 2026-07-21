package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 号源表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">slot 表 DDL</a>
 */
@Data
public class Slot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 schedule.id */
    private Long scheduleId;

    /** 号源序号 */
    private Integer slotSeq;

    /** 号源开始时间 */
    private LocalTime slotStart;

    /** 号源结束时间 */
    private LocalTime slotEnd;

    /** 状态：AVAILABLE-可用 / LOCKED-锁定中 / BOOKED-已预约 / CANCELLED-已取消 */
    private String status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
