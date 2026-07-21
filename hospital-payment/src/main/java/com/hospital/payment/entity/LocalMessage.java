package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">local_message 表 DDL</a>
 */
@Data
public class LocalMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 消息ID（UUID） */
    private String messageId;

    /** RabbitMQ Exchange */
    private String exchange;

    /** RabbitMQ Routing Key */
    private String routingKey;

    /** 消息体（JSON） */
    private String messageBody;

    /** 状态：PENDING-待发送 / SENT-已发送 / FAILED-发送失败 */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 失败原因 */
    private String failReason;

    /** 业务类型 */
    private String businessType;

    /** 业务ID */
    private String businessId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
