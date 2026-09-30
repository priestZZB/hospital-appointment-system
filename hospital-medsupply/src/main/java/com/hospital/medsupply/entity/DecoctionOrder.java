package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 中药代煎订单实体
 * <p>
 * 处方为中药饮片（HERBAL）时可下单：自煎（SELF）/ 医院代煎（HOSPITAL，生成取药凭证码与代煎费），
 * 状态机 PENDING → DECOCTING → READY → DISPENSED，CANCELLED 可从任意前置态取消。
 *
 * @see V8__pharmacy_extension.sql — decoction_order 表 DDL
 */
@Data
public class DecoctionOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 关联 clinic_db.prescription.id（应用层引用） */
    private Long prescriptionId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 剂数 */
    private Integer doses;

    /** 代煎类型: SELF-自煎 / HOSPITAL-医院代煎 */
    private String decoctionType;

    /** 状态: PENDING-待煎 / DECOCTING-煎制中 / READY-待取药 / DISPENSED-已发药 / CANCELLED-已取消 */
    private String status;

    /** 取药凭证码（HOSPITAL 类型生成） */
    private String pickupCode;

    /** 代煎费（HOSPITAL 类型 = 剂数 × 3.00） */
    private BigDecimal feeAmount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
