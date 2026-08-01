package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检查报告表实体
 * <p>
 * 报告附件（PDF/图片）存储至 MinIO。
 *
 * @see V1__init.sql — exam_report 表 DDL
 */
@Data
public class ExamReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 exam_application.id（一对一） */
    private Long applicationId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 检查描述 */
    private String reportDesc;

    /** 检查结果/诊断 */
    private String reportResult;

    /** 报告附件 MinIO URL */
    private String attachmentUrl;

    /** 附件文件名 */
    private String attachmentName;

    /** 报告录入人ID */
    private Long operatorId;

    /** DRAFT-草稿 / PUBLISHED-已发布 */
    private String status;

    /** 报告完成时间 */
    private LocalDateTime completeTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
