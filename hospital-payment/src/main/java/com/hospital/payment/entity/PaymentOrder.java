package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">payment_order 表 DDL</a>
 */
@Data
public class PaymentOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 订单编号（唯一） */
    private String orderNo;

    /** 关联 clinic_db.appointment.id */
    private Long appointmentId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 订单金额 */
    private BigDecimal amount;

    /** 订单类型：REGISTRATION-挂号费 / DRUG-药品费 / EXAM-检查费 */
    private String orderType;

    /** 状态：PENDING-待支付 / PAID-已支付 / TIMEOUT-已超时 / CANCELLED-已取消 / REFUNDED-已退款 */
    private String status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 支付方式：SIMULATED-模拟支付 */
    private String payMethod;

    /** 订单过期时间 */
    private LocalDateTime expireTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
