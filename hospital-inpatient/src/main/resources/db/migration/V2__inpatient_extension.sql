-- ============================================================
-- inpatient_db V2__inpatient_extension.sql
-- 住院扩展：护理病历(E2) / 院内会诊(E1) / 手术申请(E6) / 病案首页(E3)
-- 幂等：CREATE TABLE IF NOT EXISTS
-- ============================================================

-- 1. nursing_record 护理病历（护理记录单/出入量）
CREATE TABLE IF NOT EXISTS `nursing_record` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id` BIGINT       NOT NULL                COMMENT '关联 admission.id',
    `record_type`  VARCHAR(20)  NOT NULL DEFAULT 'ROUTINE' COMMENT 'ROUTINE-护理记录 / IO-出入量',
    `content`      TEXT         DEFAULT NULL            COMMENT '护理内容',
    `intake_ml`    INT          DEFAULT NULL            COMMENT '入量(ml)',
    `output_ml`    INT          DEFAULT NULL            COMMENT '出量(ml)',
    `nurse_id`     BIGINT       NOT NULL                COMMENT '记录护士ID',
    `record_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_nursing_admission` (`admission_id`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='护理病历表';

-- 2. inpatient_consult 住院院内会诊
CREATE TABLE IF NOT EXISTS `inpatient_consult` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`     BIGINT       NOT NULL                COMMENT '关联 admission.id',
    `patient_id`       BIGINT       NOT NULL                COMMENT '患者ID（冗余）',
    `request_dept_id`  BIGINT       NOT NULL                COMMENT '申请科室ID',
    `request_doctor_id` BIGINT      NOT NULL                COMMENT '申请医生ID',
    `target_dept_id`   BIGINT       NOT NULL                COMMENT '受邀科室ID',
    `target_doctor_id` BIGINT       DEFAULT NULL            COMMENT '受邀医生ID（空=科室会诊）',
    `reason`           VARCHAR(500) NOT NULL                COMMENT '会诊目的/病情摘要',
    `opinion`          TEXT         DEFAULT NULL            COMMENT '会诊意见',
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待处理 / ACCEPTED-已接受 / COMPLETED-已完成 / REJECTED-已拒绝',
    `handle_doctor_id` BIGINT       DEFAULT NULL            COMMENT '处理医生ID',
    `handle_time`      DATETIME     DEFAULT NULL            COMMENT '处理时间',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_consult_admission` (`admission_id`),
    KEY `idx_consult_target` (`target_dept_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='住院院内会诊表';

-- 3. surgery_apply 手术申请（迭代6 建单，迭代10 排台执行）
CREATE TABLE IF NOT EXISTS `surgery_apply` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`   BIGINT       NOT NULL                COMMENT '关联 admission.id（门诊手术可为空语义另扩）',
    `patient_id`     BIGINT       NOT NULL                COMMENT '患者ID（冗余）',
    `surgery_name`   VARCHAR(200) NOT NULL                COMMENT '手术名称',
    `anesthesia_type` VARCHAR(30) DEFAULT NULL            COMMENT '全麻/局麻/椎管内/神经阻滞',
    `apply_doctor_id` BIGINT      NOT NULL                COMMENT '申请医生ID',
    `scheduled_time` DATETIME     DEFAULT NULL            COMMENT '拟手术时间',
    `operating_room` VARCHAR(50)  DEFAULT NULL            COMMENT '手术室',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待安排 / SCHEDULED-已排台 / COMPLETED-已完成 / CANCELLED-已取消',
    `remark`         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_surgery_admission` (`admission_id`),
    KEY `idx_surgery_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='手术申请表';

-- 4. medical_record_home 住院病案首页（出院归档）
CREATE TABLE IF NOT EXISTS `medical_record_home` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`  BIGINT        NOT NULL                COMMENT '关联 admission.id',
    `patient_id`    BIGINT        NOT NULL                COMMENT '患者ID',
    `department_id` BIGINT        NOT NULL                COMMENT '出院科室',
    `doctor_id`     BIGINT        NOT NULL                COMMENT '主治医生ID',
    `admission_time` DATETIME     NOT NULL                COMMENT '入院时间',
    `discharge_time` DATETIME     NOT NULL                COMMENT '出院时间',
    `hospital_days` INT           NOT NULL DEFAULT 0      COMMENT '住院天数',
    `discharge_diag` VARCHAR(500) NOT NULL                COMMENT '出院诊断',
    `main_operation` VARCHAR(200) DEFAULT NULL            COMMENT '主要手术操作',
    `fee_bed`       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '床位费',
    `fee_drug`      DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '药品费',
    `fee_exam`      DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '检查费',
    `fee_lab`       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '检验费',
    `fee_other`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '其他费用',
    `fee_total`     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '费用总额',
    `settlement_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '结算金额（多退少补）',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_home_admission` (`admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='住院病案首页表';
