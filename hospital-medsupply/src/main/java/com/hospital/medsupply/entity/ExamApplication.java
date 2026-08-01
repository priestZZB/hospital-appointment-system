package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检查申请表实体
 * <p>
 * 接收 clinic-service 通过 Feign 发来的检查/检验申请。
 *
 * @see V1__init.sql — exam_application 表 DDL
 */
@Data
public class ExamApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 申请编号 */
    private String applicationNo;

    /** 关联 clinic_db.medical_record.id（应用层引用） */
    private Long medicalRecordId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 关联 clinic_db.doctor.id */
    private Long doctorId;

    /** 关联 exam_item.id */
    private Long examItemId;

    /** 检查项目名称（冗余） */
    private String examItemName;

    /** 项目类型（冗余） */
    private String itemType;

    /** 申请备注 */
    private String applyRemark;

    /** PENDING-待执行 / EXECUTING-执行中 / COMPLETED-已完成 / CANCELLED-已取消 */
    private String status;

    /** 申请时间 */
    private LocalDateTime createTime;
}
