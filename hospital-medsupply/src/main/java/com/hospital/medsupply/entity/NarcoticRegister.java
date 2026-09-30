package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 麻精药品五专登记实体（五专之专账）
 * <p>
 * 麻醉药品/精神药品入出存全留痕流水，balance 为登记后结存。
 *
 * @see V8__pharmacy_extension.sql — narcotic_register 表 DDL
 */
@Data
public class NarcoticRegister implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 drug.id */
    private Long drugId;

    /** 关联 clinic_db.prescription.id（应用层引用，可空） */
    private Long prescriptionId;

    /** 关联 patient_db.patient.id（可空） */
    private Long patientId;

    /** 动作: INBOUND-入库 / OUTBOUND-发药 / RETURN-退药 / SCRAP-报损 */
    private String action;

    /** 数量（正数） */
    private Integer quantity;

    /** 登记后结存 */
    private Integer balance;

    /** 操作人ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
