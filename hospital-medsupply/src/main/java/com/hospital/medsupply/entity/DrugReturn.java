package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退药单实体
 * <p>
 * 发药后退药冲账，库存回冲 + 金额冲账。
 *
 * @see V8__pharmacy_extension.sql — drug_return 表 DDL
 */
@Data
public class DrugReturn implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 退药单号 */
    private String returnNo;

    /** 关联 clinic_db.prescription.id（应用层引用） */
    private Long prescriptionId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 关联 drug.id */
    private Long drugId;

    /** 退药数量 */
    private Integer quantity;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退药原因 */
    private String reason;

    /** 操作人ID */
    private Long operatorId;

    /** 状态: COMPLETED-已完成 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
