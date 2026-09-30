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

    /** 结构化-所见（D4，CLOB），医生开报告时套模板或自由书写 */
    private String findings;

    /** 结构化-印象（D4） */
    private String conclusion;

    /** 报告附件 MinIO URL */
    private String attachmentUrl;

    /** 附件文件名 */
    private String attachmentName;

    /** 报告录入人ID */
    private Long operatorId;

    /** 报告审核人ID */
    private Long auditorId;

    /** 报告审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见 */
    private String auditComment;

    /** DRAFT-草稿 / PENDING_AUDIT-待审核 / PUBLISHED-已发布 / REJECTED-已驳回 */
    private String status;

    /** 报告完成时间 */
    private LocalDateTime completeTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
