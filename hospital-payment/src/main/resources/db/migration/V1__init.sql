-- ============================================================
-- payment_db V1__init.sql
-- 支付调度服务：4 张表
-- ============================================================

-- ==================== 1. payment_order（支付订单表） ====================
CREATE TABLE `payment_order` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`       VARCHAR(32)   NOT NULL                COMMENT '订单编号（唯一）',
    `appointment_id` BIGINT        NOT NULL                COMMENT '关联 clinic_db.appointment.id（应用层引用）',
    `patient_id`     BIGINT        NOT NULL                COMMENT '关联 patient_db.patient.id',
    `amount`         DECIMAL(10,2) NOT NULL                COMMENT '订单金额',
    `order_type`     VARCHAR(20)   NOT NULL                COMMENT '订单类型：REGISTRATION-挂号费 / DRUG-药品费 / EXAM-检查费',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待支付 / PAID-已支付 / TIMEOUT-已超时 / CANCELLED-已取消 / REFUNDED-已退款',
    `pay_time`       DATETIME      DEFAULT NULL            COMMENT '支付时间',
    `pay_method`     VARCHAR(20)   DEFAULT NULL            COMMENT '支付方式：SIMULATED-模拟支付 / WECHAT-微信 / ALIPAY-支付宝',
    `expire_time`    DATETIME      NOT NULL                COMMENT '订单过期时间（创建后30分钟）',
    `cancel_time`    DATETIME      DEFAULT NULL            COMMENT '取消时间',
    `version`        INT           DEFAULT 0               COMMENT '乐观锁版本号',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_order_no` (`order_no`),
    KEY `idx_pay_appointment` (`appointment_id`),
    KEY `idx_pay_patient` (`patient_id`),
    KEY `idx_pay_status` (`status`),
    KEY `idx_pay_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付订单表';

-- ==================== 2. local_message（本地消息表） ====================
CREATE TABLE `local_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id`      VARCHAR(64)  NOT NULL                COMMENT '消息ID（UUID）',
    `exchange`        VARCHAR(100) NOT NULL                COMMENT 'RabbitMQ Exchange',
    `routing_key`     VARCHAR(100) NOT NULL                COMMENT 'RabbitMQ Routing Key',
    `message_body`    TEXT         NOT NULL                COMMENT '消息体（JSON）',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待发送 / SENT-已发送 / FAILED-发送失败',
    `retry_count`     INT          DEFAULT 0               COMMENT '已重试次数',
    `max_retry`       INT          DEFAULT 3               COMMENT '最大重试次数',
    `next_retry_time` DATETIME     DEFAULT NULL            COMMENT '下次重试时间',
    `fail_reason`     VARCHAR(500) DEFAULT NULL            COMMENT '失败原因',
    `business_type`   VARCHAR(50)  NOT NULL                COMMENT '业务类型：ORDER_CREATE / ORDER_TIMEOUT / ORDER_PAID / NOTIFICATION',
    `business_id`     VARCHAR(64)  NOT NULL                COMMENT '业务ID（如订单号）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_local_msg_id` (`message_id`),
    KEY `idx_msg_status` (`status`),
    KEY `idx_msg_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='本地消息表';

-- ==================== 3. refund_record（退款记录表） ====================
CREATE TABLE `refund_record` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `refund_no`        VARCHAR(32)   NOT NULL                COMMENT '退款编号',
    `payment_order_id` BIGINT        NOT NULL                COMMENT '关联 payment_order.id',
    `appointment_id`   BIGINT        NOT NULL                COMMENT '关联 clinic_db.appointment.id',
    `refund_amount`    DECIMAL(10,2) NOT NULL                COMMENT '退款金额',
    `refund_reason`    VARCHAR(255)  NOT NULL                COMMENT '退款原因',
    `refund_type`      VARCHAR(30)   NOT NULL                COMMENT '退款类型：PATIENT_CANCEL-患者取消 / DOCTOR_STOP-医生停诊 / SYSTEM_TIMEOUT-系统超时',
    `status`           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待退款 / COMPLETED-已退款 / FAILED-退款失败',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_refund_order` (`payment_order_id`),
    KEY `idx_refund_appointment` (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退款记录表';

-- ==================== 4. notification（站内信通知表） ====================
CREATE TABLE `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `patient_id`  BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `title`       VARCHAR(200) NOT NULL                COMMENT '通知标题',
    `content`     TEXT         NOT NULL                COMMENT '通知内容',
    `notify_type` VARCHAR(30)  NOT NULL                COMMENT '通知类型：APPOINTMENT-预约通知 / PAYMENT-支付通知 / CANCEL-取消通知 / STOP-停诊通知 / SYSTEM-系统通知',
    `related_id`  VARCHAR(64)  DEFAULT NULL            COMMENT '关联业务ID',
    `is_read`     TINYINT      DEFAULT 0               COMMENT '0-未读 1-已读',
    `read_time`   DATETIME     DEFAULT NULL            COMMENT '阅读时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_notify_patient` (`patient_id`),
    KEY `idx_notify_is_read` (`is_read`),
    KEY `idx_notify_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内信通知表';
