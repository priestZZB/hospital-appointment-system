-- ============================================================
-- medsupply_db V5：门诊输液 + 检查执行登记
-- 说明：对齐真实医院门诊业务
--   1. 新增 infusion_order（输液单）、infusion_record（输液执行记录）
--   2. exam_application 增加检查执行登记字段
-- ============================================================

-- ==================== 8. infusion_order（输液单表） ====================
-- 医生开具输液医嘱后生成输液单，由护士站执行
CREATE TABLE `infusion_order` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `infusion_no`       VARCHAR(32)   NOT NULL                COMMENT '输液单编号',
    `medical_record_id` BIGINT        NOT NULL                COMMENT '关联 clinic_db.medical_record.id（应用层引用）',
    `patient_id`        BIGINT        NOT NULL                COMMENT '关联 patient_db.patient.id',
    `doctor_id`         BIGINT        NOT NULL                COMMENT '关联 clinic_db.doctor.id',
    `drug_id`           BIGINT        NOT NULL                COMMENT '关联 drug.id（输液药品）',
    `drug_name`         VARCHAR(200)  NOT NULL                COMMENT '药品名称（冗余）',
    `dosage`            VARCHAR(50)   NOT NULL                COMMENT '单次用量',
    `usage_method`      VARCHAR(50)   NOT NULL                COMMENT '用法：IV-静脉注射 / IVDRIP-静脉滴注',
    `frequency`         VARCHAR(50)   NOT NULL                COMMENT '频次：QD/BID/TID',
    `days`              INT           NOT NULL                COMMENT '输液天数',
    `skin_test_required` TINYINT      DEFAULT 0               COMMENT '是否需皮试：0-否 1-是',
    `unit_price`        DECIMAL(10,2) NOT NULL                COMMENT '单价（明细计价）',
    `total_amount`      DECIMAL(10,2) NOT NULL                COMMENT '总金额 = 单价 × 天数 × 频次',
    `pay_status`        VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT '缴费状态：UNPAID-未缴费 / PAID-已缴费 / REFUNDED-已退款',
    `status`            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '执行状态：PENDING-待执行 / IN_PROGRESS-执行中 / COMPLETED-已完成 / CANCELLED-已取消',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_infusion_no` (`infusion_no`),
    KEY `idx_infusion_patient` (`patient_id`),
    KEY `idx_infusion_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='输液单表';

-- ==================== 9. infusion_record（输液执行记录表） ====================
-- 护士每次执行输液操作时生成一条记录
CREATE TABLE `infusion_record` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `infusion_order_id`  BIGINT       NOT NULL                COMMENT '关联 infusion_order.id',
    `record_type`        VARCHAR(20)  NOT NULL                COMMENT '执行类型：SKIN_TEST-皮试 / PREPARE-配液 / START-开始输液 / END-结束输液 / OBSERVE-观察记录',
    `record_content`     VARCHAR(500) DEFAULT NULL            COMMENT '执行内容/观察记录',
    `skin_test_result`   VARCHAR(20)  DEFAULT NULL            COMMENT '皮试结果：NEGATIVE-阴性 / POSITIVE-阳性',
    `drop_rate`          INT          DEFAULT NULL            COMMENT '滴速（滴/分钟）',
    `operator_id`        BIGINT       DEFAULT NULL            COMMENT '执行护士ID',
    `operator_name`      VARCHAR(50)  DEFAULT NULL            COMMENT '执行护士姓名',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    PRIMARY KEY (`id`),
    KEY `idx_infusion_record_order` (`infusion_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='输液执行记录表';

-- ==================== exam_application 增加执行登记字段 ====================
ALTER TABLE `exam_application`
    ADD COLUMN `exec_time` DATETIME DEFAULT NULL COMMENT '检查执行时间' AFTER `status`,
    ADD COLUMN `exec_operator_id` BIGINT DEFAULT NULL COMMENT '执行技师ID' AFTER `exec_time`;
