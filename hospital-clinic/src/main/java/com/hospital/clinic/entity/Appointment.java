package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约订单表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">appointment 表 DDL</a>
 */
@Data
public class Appointment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 预约编号（唯一） */
    private String appointmentNo;

    /** 关联 patient_db.patient.id（应用层引用） */
    private Long patientId;

    /** 关联 slot.id */
    private Long slotId;

    /** 关联 schedule.id（冗余） */
    private Long scheduleId;

    /** 关联 doctor.id（冗余） */
    private Long doctorId;

    /** 关联 department.id（冗余） */
    private Long departmentId;

    /** 就诊日期（冗余） */
    private LocalDate appointmentDate;

    /** 时段：AM/PM（冗余） */
    private String period;

    /** 号源序号（冗余） */
    private Integer slotSeq;

    /** 挂号费（冗余） */
    private BigDecimal registerFee;

    /** 订单状态：PENDING_PAY-待支付 / PAID-已支付 / CANCELLED-已取消 / TIMEOUT-已超时 / REFUNDED-已退款 */
    private String orderStatus;

    /** 就诊状态：CHECKED_IN-已签到 / WAITING-排队中 / CALLED-已叫号 / IN_PROGRESS-就诊中 / COMPLETED-已完成 / MISSED-过号 */
    private String visitStatus;

    /** 是否复诊：0-初诊 1-复诊 */
    private Integer isRevisit;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因 */
    private String cancelReason;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
