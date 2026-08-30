package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 处方明细表实体
 *
 * @see V1__init.sql — prescription_item 表 DDL
 */
@Data
public class PrescriptionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 prescription.id */
    private Long prescriptionId;

    /** 关联 medsupply_db.drug.id（应用层引用） */
    private Long drugId;

    /** 药品名称（冗余） */
    private String drugName;

    /** 规格 */
    private String specification;

    /** 单次用量 */
    private String dosage;

    /** 用法：ORAL-口服 / EXTERNAL-外用 / IV-静脉注射 / IM-肌肉注射 */
    private String usageMethod;

    /** 频次：QD-每日1次 / BID-每日2次 / TID-每日3次 / QN-睡前 */
    private String frequency;

    /** 用药天数 */
    private Integer days;

    /** 总量 */
    private Integer quantity;

    /** 单价（开单时取药品参考价，明细计价） */
    private java.math.BigDecimal unitPrice;

    /** 单位：TABLET-片 / VIAL-支 / BOTTLE-瓶 / BOX-盒 */
    private String unit;

    /** 备注 */
    private String remark;
}
