package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 输液单表实体
 * <p>
 * 医生开具输液医嘱后生成输液单，由护士站执行。
 *
 * @see V5__infusion_and_exam_exec.sql — infusion_order 表 DDL
 */
@Data
public class InfusionOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 输液单编号 */
    private String infusionNo;

    /** 关联 clinic_db.medical_record.id（应用层引用） */
    private Long medicalRecordId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 关联 clinic_db.doctor.id */
    private Long doctorId;

    /** 关联 drug.id（输液药品） */
    private Long drugId;

    /** 药品名称（冗余） */
    private String drugName;

    /** 单次用量 */
    private String dosage;

    /** 用法：IV-静脉注射 / IVDRIP-静脉滴注 */
    private String usageMethod;

    /** 频次：QD/BID/TID */
    private String frequency;

    /** 输液天数 */
    private Integer days;

    /** 是否需皮试：0-否 1-是 */
    private Integer skinTestRequired;

    /** 单价（明细计价） */
    private BigDecimal unitPrice;

    /** 总金额 = 单价 × 天数 × 频次 */
    private BigDecimal totalAmount;

    /** 缴费状态：UNPAID-未缴费 / PAID-已缴费 / REFUNDED-已退款 */
    private String payStatus;

    /** 执行状态：PENDING-待执行 / IN_PROGRESS-执行中 / COMPLETED-已完成 / CANCELLED-已取消 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
