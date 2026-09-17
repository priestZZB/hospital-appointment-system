-- ============================================================
-- clinic_db V8：会诊 / 转诊 / 随访 / 医疗证明（功能补全第一批）
-- 说明：
--   1. 会诊请求表 consultation_request：医生发起会诊 → 会诊医生处理 → 结论
--   2. 转诊单 referral_order：转出/转入科室、状态机、与病历 referral 字段打通
--   3. 随访计划 follow_up_plan + 随访记录 follow_up_record
--   4. 医疗证明 medical_certificate（诊断证明/病假条/转诊单/医疗建议）
--   幂等：全部 INSERT ... SELECT + NOT EXISTS
-- ============================================================

-- 1. 会诊请求
CREATE TABLE IF NOT EXISTS `consultation_request` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `request_no`      VARCHAR(32)  NOT NULL                COMMENT '会诊申请编号',
    `medical_record_id` BIGINT     NOT NULL                COMMENT '关联 medical_record.id',
    `patient_id`      BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `apply_dept_id`   BIGINT       NOT NULL                COMMENT '申请科室ID',
    `apply_doctor_id` BIGINT       NOT NULL                COMMENT '申请医生ID',
    `target_dept_id`  BIGINT       NOT NULL                COMMENT '会诊目标科室ID',
    `target_doctor_id` BIGINT      DEFAULT NULL            COMMENT '会诊医生ID（可选）',
    `reason`          VARCHAR(1000) DEFAULT NULL           COMMENT '会诊原因/病情摘要',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待会诊 / ACCEPTED-已接受 / COMPLETED-已完成 / REJECTED-已拒绝',
    `consult_opinion` TEXT         DEFAULT NULL            COMMENT '会诊意见',
    `consult_doctor_id` BIGINT     DEFAULT NULL            COMMENT '会诊医生ID（处理人）',
    `consult_time`    DATETIME     DEFAULT NULL            COMMENT '会诊时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consult_request_no` (`request_no`),
    KEY `idx_consult_req_target` (`target_dept_id`, `status`),
    KEY `idx_consult_req_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会诊请求表';

-- 2. 转诊单
CREATE TABLE IF NOT EXISTS `referral_order` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `referral_no`     VARCHAR(32)  NOT NULL                COMMENT '转诊单编号',
    `medical_record_id` BIGINT     NOT NULL                COMMENT '关联 medical_record.id',
    `patient_id`      BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `from_dept_id`    BIGINT       NOT NULL                COMMENT '转出科室ID',
    `from_doctor_id`  BIGINT       NOT NULL                COMMENT '转出医生ID',
    `to_dept_id`      BIGINT       NOT NULL                COMMENT '转入科室ID',
    `reason`          VARCHAR(1000) DEFAULT NULL           COMMENT '转诊原因',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待接收 / ACCEPTED-已接收 / COMPLETED-已完成 / REJECTED-已退回',
    `accept_time`     DATETIME     DEFAULT NULL            COMMENT '接收时间',
    `complete_time`   DATETIME     DEFAULT NULL            COMMENT '完成时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_referral_no` (`referral_no`),
    KEY `idx_referral_to_dept` (`to_dept_id`, `status`),
    KEY `idx_referral_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='转诊单表';

-- 3. 随访计划
CREATE TABLE IF NOT EXISTS `follow_up_plan` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `patient_id`      BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `medical_record_id` BIGINT     DEFAULT NULL            COMMENT '关联 medical_record.id（可空）',
    `doctor_id`       BIGINT       NOT NULL                COMMENT '创建医生ID',
    `follow_date`     DATE         NOT NULL                COMMENT '计划随访日期',
    `follow_method`   VARCHAR(50)  NOT NULL                COMMENT '随访方式：PHONE-电话 / VISIT-门诊复诊 / WECHAT-微信 / OTHER-其他',
    `template`        VARCHAR(500) DEFAULT NULL            COMMENT '随访模板/内容',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待随访 / DONE-已完成 / OVERDUE-已逾期 / CANCELLED-已取消',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_followup_patient` (`patient_id`),
    KEY `idx_followup_doctor` (`doctor_id`),
    KEY `idx_followup_date` (`follow_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='随访计划表';

-- 4. 随访记录
CREATE TABLE IF NOT EXISTS `follow_up_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_id`         BIGINT       NOT NULL                COMMENT '关联 follow_up_plan.id',
    `patient_id`      BIGINT       NOT NULL                COMMENT '患者ID（冗余）',
    `doctor_id`       BIGINT       NOT NULL                COMMENT '随访医生ID',
    `content`         VARCHAR(2000) DEFAULT NULL           COMMENT '随访内容/患者反馈',
    `next_follow_date` DATE        DEFAULT NULL            COMMENT '下次随访日期（可空）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_followup_record_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='随访记录表';

-- 5. 医疗证明
CREATE TABLE IF NOT EXISTS `medical_certificate` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cert_no`         VARCHAR(32)  NOT NULL                COMMENT '证明编号',
    `cert_type`       VARCHAR(30)  NOT NULL                COMMENT 'DIAGNOSIS-诊断证明 / SICK_LEAVE-病假条 / REFERRAL-转诊单 / MEDICAL_ADVICE-医疗建议',
    `patient_id`      BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `doctor_id`       BIGINT       NOT NULL                COMMENT '开具医生ID',
    `medical_record_id` BIGINT     DEFAULT NULL            COMMENT '关联 medical_record.id（可空）',
    `content`         TEXT         DEFAULT NULL            COMMENT '证明内容',
    `days`            INT          DEFAULT NULL            COMMENT '建议休假天数（病假条）',
    `start_date`      DATE         DEFAULT NULL            COMMENT '起始日期（病假/建议）',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED-已开具 / CANCELLED-已作废',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_no` (`cert_no`),
    KEY `idx_cert_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='医疗证明表';
