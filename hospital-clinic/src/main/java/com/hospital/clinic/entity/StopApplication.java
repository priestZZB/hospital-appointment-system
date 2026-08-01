package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停诊申请表实体
 *
 * @see V1__init.sql — stop_application 表 DDL
 */
@Data
public class StopApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 schedule.id */
    private Long scheduleId;

    /** 关联 doctor.id（申请医生） */
    private Long doctorId;

    /** 停诊原因 */
    private String applyReason;

    /** PENDING-待审批 / APPROVED-已通过 / REJECTED-已驳回 */
    private String status;

    /** 审批意见 */
    private String approveComment;

    /** 审批人ID（关联 auth_db.user.id） */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 受影响的待签到预约数 */
    private Integer affectedCount;

    /** 退款总金额 */
    private BigDecimal refundTotal;

    /** 申请时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
