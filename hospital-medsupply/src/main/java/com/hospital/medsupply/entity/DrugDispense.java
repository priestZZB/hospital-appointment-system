package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发药记录表实体
 * <p>
 * 处方审核通过后进行发药，发药时乐观锁扣减药品库存。
 *
 * @see V1__init.sql — drug_dispense 表 DDL
 */
@Data
public class DrugDispense implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 clinic_db.prescription.id（应用层引用） */
    private Long prescriptionId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** PENDING_REVIEW-待审核 / REVIEW_PASSED-审核通过 / REVIEW_REJECTED-审核驳回 / DISPENSED-已发药 */
    private String status;

    /** 审核人ID */
    private Long reviewOperatorId;

    /** 审核意见 */
    private String reviewComment;

    /** 四查十对核查结果（JSON，如 [{"item":"查处方","result":"PASS","remark":"..."}...]） */
    private String reviewCheck;

    /** 审核时间 */
    private LocalDateTime reviewTime;

    /** 发药人ID */
    private Long dispenseOperatorId;

    /** 发药时间 */
    private LocalDateTime dispenseTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
