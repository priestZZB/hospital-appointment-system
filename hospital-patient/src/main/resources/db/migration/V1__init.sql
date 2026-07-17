-- ============================================================
-- patient_db V1__init.sql
-- 患者档案中心：3 张表
-- ============================================================

-- ==================== 1. patient（患者电子档案表） ====================
CREATE TABLE `patient` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`            BIGINT       DEFAULT NULL            COMMENT '关联 auth_db.user.id（应用层引用）',
    `name`               VARCHAR(50)  NOT NULL                COMMENT '姓名',
    `gender`             TINYINT      DEFAULT 0               COMMENT '0-未知 1-男 2-女',
    `birth_date`         DATE         DEFAULT NULL            COMMENT '出生日期',
    `id_card`            VARCHAR(18)  DEFAULT NULL            COMMENT '身份证号',
    `phone`              VARCHAR(20)  NOT NULL                COMMENT '联系电话',
    `emergency_contact`  VARCHAR(50)  DEFAULT NULL            COMMENT '紧急联系人姓名',
    `emergency_phone`    VARCHAR(20)  DEFAULT NULL            COMMENT '紧急联系人电话',
    `verify_status`      TINYINT      DEFAULT 0               COMMENT '实名认证：0-未认证 1-审核中 2-已认证 3-认证驳回',
    `id_card_front_url`  VARCHAR(500) DEFAULT NULL            COMMENT '身份证正面照片 MinIO URL',
    `id_card_back_url`   VARCHAR(500) DEFAULT NULL            COMMENT '身份证反面照片 MinIO URL',
    `verify_comment`     VARCHAR(255) DEFAULT NULL            COMMENT '认证审核备注',
    `avatar_url`         VARCHAR(500) DEFAULT NULL            COMMENT '头像 MinIO URL',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_patient_user_id` (`user_id`),
    KEY `idx_patient_id_card` (`id_card`),
    KEY `idx_patient_phone` (`phone`),
    KEY `idx_patient_verify_status` (`verify_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='患者电子档案表';

-- ==================== 2. visit_card（虚拟就诊卡表） ====================
CREATE TABLE `visit_card` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `patient_id`  BIGINT      NOT NULL                COMMENT '关联 patient.id',
    `card_no`     VARCHAR(20) NOT NULL                COMMENT '就诊卡号（VIP + 8 位数字）',
    `status`      TINYINT     DEFAULT 1               COMMENT '1-正常 0-挂失',
    `issue_date`  DATE        NOT NULL                COMMENT '发卡日期',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_card_no` (`card_no`),
    KEY `idx_card_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='虚拟就诊卡表';

-- ==================== 3. allergy（过敏史表） ====================
CREATE TABLE `allergy` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `patient_id`       BIGINT       NOT NULL                COMMENT '关联 patient.id',
    `allergen`         VARCHAR(100) NOT NULL                COMMENT '过敏原名称',
    `reaction_type`    VARCHAR(50)  DEFAULT NULL            COMMENT '反应类型：RASH-皮疹 / DYSPNEA-呼吸困难 / SHOCK-休克 / OTHER-其他',
    `severity`         VARCHAR(20)  DEFAULT NULL            COMMENT '严重程度：MILD-轻度 / MODERATE-中度 / SEVERE-重度',
    `source`           VARCHAR(20)  NOT NULL DEFAULT 'PATIENT' COMMENT '来源：PATIENT-患者自填 / DOCTOR-医生录入',
    `source_doctor_id` BIGINT       DEFAULT NULL            COMMENT '若来源为医生录入，记录医生ID',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_allergy_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='过敏史表';
