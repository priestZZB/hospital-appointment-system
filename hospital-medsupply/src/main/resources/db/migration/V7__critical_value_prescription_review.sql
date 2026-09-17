-- ============================================================
-- medsupply_db V7：危急值 + 处方点评（迭代6 功能补全）
-- 说明：
--   1. critical_value 危急值：检验/检查结果命中阈值 → 上报 → 复核闭环
--   2. prescription_review 处方点评：药师对处方点评（合理/不合理/问题分类）
--   幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

-- 1. 危急值表
CREATE TABLE IF NOT EXISTS `critical_value` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_id`         BIGINT       NOT NULL                COMMENT '关联 exam_report.id',
    `application_id`    BIGINT       NOT NULL                COMMENT '关联 exam_application.id',
    `patient_id`        BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `item_name`         VARCHAR(200) NOT NULL                COMMENT '检查/检验项目名称',
    `result_value`      VARCHAR(200) NOT NULL                COMMENT '结果值（原始文本）',
    `reference_range`   VARCHAR(200) DEFAULT NULL            COMMENT '参考范围',
    `critical_level`    VARCHAR(20)  NOT NULL DEFAULT 'HIGH' COMMENT '级别：HIGH-高 / LOW-低',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待复核 / CONFIRMED-已复核 / RESOLVED-已处置',
    `reporter_id`       BIGINT       DEFAULT NULL            COMMENT '上报人ID（技师）',
    `confirm_doctor_id` BIGINT       DEFAULT NULL            COMMENT '复核医生ID',
    `confirm_comment`   VARCHAR(500) DEFAULT NULL            COMMENT '复核意见',
    `confirm_time`      DATETIME     DEFAULT NULL            COMMENT '复核时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_critical_status` (`status`),
    KEY `idx_critical_patient` (`patient_id`),
    KEY `idx_critical_report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='危急值表';

-- 2. 处方点评表
CREATE TABLE IF NOT EXISTS `prescription_review` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `prescription_id`   BIGINT       NOT NULL                COMMENT '关联 clinic_db.prescription.id',
    `patient_id`        BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `pharmacist_id`     BIGINT       NOT NULL                COMMENT '点评药师ID',
    `rating`            VARCHAR(20)  NOT NULL DEFAULT 'REASONABLE' COMMENT '评价：REASONABLE-合理 / UNREASONABLE-不合理',
    `problem_type`      VARCHAR(100) DEFAULT NULL            COMMENT '问题分类：剂量/配伍/禁忌/重复用药/其他',
    `comment`           VARCHAR(1000) DEFAULT NULL           COMMENT '点评意见',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_prescription_review_prescription` (`prescription_id`),
    KEY `idx_prescription_review_pharmacist` (`pharmacist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='处方点评表';
