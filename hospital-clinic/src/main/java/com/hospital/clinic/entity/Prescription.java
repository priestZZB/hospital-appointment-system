package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方主表实体
 *
 * @see V1__init.sql — prescription 表 DDL
 * @see V9__prescription_type_herbal.sql — 处方类型（B4）与中药饮片处方头扩展（B2）
 */
@Data
public class Prescription implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 处方编号 */
    private String prescriptionNo;

    /** 关联 medical_record.id */
    private Long medicalRecordId;

    /** 关联 patient_db.patient.id（冗余） */
    private Long patientId;

    /** 关联 doctor.id（冗余） */
    private Long doctorId;

    /** PENDING_REVIEW-待审核 / REVIEW_PASSED-审核通过 / REVIEW_REJECTED-审核驳回 / DISPENSED-已发药 */
    private String status;

    /** 审核意见 */
    private String reviewComment;

    /** 处方总金额/实收金额（缴费回写时写入，默认 0） */
    private BigDecimal totalAmount;

    /** 缴费状态：UNPAID-待缴费 / PAID-已缴费 / REFUNDED-已退费 */
    private String payStatus;

    /** 处方类型：WESTERN-西药/中成药处方笺 / HERBAL-中药饮片处方笺（V9） */
    private String prescriptionType;

    /** 中药剂数（HERBAL 处方必填，如 7 剂）（V9） */
    private Integer herbalDoses;

    /** 煎服法（如：每日一剂，水煎400ml，分早晚两次温服）（V9） */
    private String herbalUsage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
