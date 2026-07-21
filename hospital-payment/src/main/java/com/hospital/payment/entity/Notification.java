package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信通知表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">notification 表 DDL</a>
 */
@Data
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型 */
    private String notifyType;

    /** 关联业务ID */
    private String relatedId;

    /** 0-未读 1-已读 */
    private Integer isRead;

    /** 阅读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
