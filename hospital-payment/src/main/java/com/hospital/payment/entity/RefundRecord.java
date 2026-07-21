package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款记录表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">refund_record 表 DDL</a>
 */
@Data
public class RefundRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 退款编号 */
    private String refundNo;

    /** 关联 payment_order.id */
    private Long paymentOrderId;

    /** 关联 clinic_db.appointment.id */
    private Long appointmentId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String refundReason;

    /** 退款类型：PATIENT_CANCEL / DOCTOR_STOP / SYSTEM_TIMEOUT */
    private String refundType;

    /** 状态：PENDING-待退款 / COMPLETED-已退款 / FAILED-退款失败 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
