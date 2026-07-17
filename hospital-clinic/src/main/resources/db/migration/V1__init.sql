-- ============================================================
-- clinic_db V1__init.sql
-- 核心诊疗服务：10 张表 + 初始数据（14 个科室）
-- ============================================================

-- ==================== 1. department（科室表） ====================
CREATE TABLE `department` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dept_name`   VARCHAR(100) NOT NULL                COMMENT '科室名称',
    `dept_code`   VARCHAR(50)  NOT NULL                COMMENT '科室编码',
    `description` VARCHAR(500) DEFAULT NULL            COMMENT '科室简介',
    `location`    VARCHAR(100) DEFAULT NULL            COMMENT '所在诊区/楼层',
    `phone`       VARCHAR(20)  DEFAULT NULL            COMMENT '科室联系电话',
    `status`      TINYINT      DEFAULT 1               COMMENT '1-启用 0-停用',
    `sort_order`  INT          DEFAULT 0               COMMENT '排序号',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_dept_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='科室表';

-- ==================== 2. doctor（医生表） ====================
CREATE TABLE `doctor` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       DEFAULT NULL            COMMENT '关联 auth_db.user.id（应用层引用）',
    `name`          VARCHAR(50)  NOT NULL                COMMENT '医生姓名',
    `gender`        TINYINT      DEFAULT 0               COMMENT '0-未知 1-男 2-女',
    `phone`         VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
    `department_id` BIGINT       NOT NULL                COMMENT '所属科室ID',
    `title`         VARCHAR(50)  NOT NULL                COMMENT '职称：CHIEF / VICE_CHIEF / ATTENDING / RESIDENT',
    `specialty`     VARCHAR(255) DEFAULT NULL            COMMENT '专长标签（逗号分隔）',
    `introduction`  TEXT         DEFAULT NULL            COMMENT '医生简介',
    `status`        TINYINT      DEFAULT 1               COMMENT '1-在职 0-离职/停诊',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_doctor_department` (`department_id`),
    KEY `idx_doctor_user_id` (`user_id`),
    KEY `idx_doctor_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='医生表';

-- ==================== 3. schedule（排班计划表） ====================
CREATE TABLE `schedule` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `doctor_id`     BIGINT       NOT NULL                COMMENT '关联 doctor.id',
    `department_id` BIGINT       NOT NULL                COMMENT '关联 department.id',
    `schedule_date` DATE         NOT NULL                COMMENT '出诊日期',
    `period`        VARCHAR(10)  NOT NULL                COMMENT 'AM-上午 / PM-下午',
    `period_start`  TIME         NOT NULL                COMMENT '时段开始时间',
    `period_end`    TIME         NOT NULL                COMMENT '时段结束时间',
    `total_slots`   INT          NOT NULL                COMMENT '该时段号源总数',
    `slot_duration` INT          DEFAULT 10              COMMENT '单次预约时长（分钟）',
    `register_fee`  DECIMAL(10,2) DEFAULT 0.00           COMMENT '挂号费',
    `status`        TINYINT      DEFAULT 1               COMMENT '1-正常 0-已取消',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schedule` (`doctor_id`, `schedule_date`, `period`),
    KEY `idx_schedule_dept_date` (`department_id`, `schedule_date`),
    KEY `idx_schedule_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='排班计划表';

-- ==================== 4. slot（号源表） ====================
CREATE TABLE `slot` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `schedule_id` BIGINT      NOT NULL                COMMENT '关联 schedule.id',
    `slot_seq`    INT         NOT NULL                COMMENT '号源序号',
    `slot_start`  TIME        NOT NULL                COMMENT '号源开始时间',
    `slot_end`    TIME        NOT NULL                COMMENT '号源结束时间',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE-可用 / LOCKED-锁定中 / BOOKED-已预约 / CANCELLED-已取消',
    `version`     INT         DEFAULT 0               COMMENT '乐观锁版本号',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slot_schedule_seq` (`schedule_id`, `slot_seq`),
    KEY `idx_slot_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='号源表';

-- ==================== 5. appointment（预约订单表） ====================
CREATE TABLE `appointment` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `appointment_no`   VARCHAR(32)   NOT NULL                COMMENT '预约编号（唯一）',
    `patient_id`       BIGINT        NOT NULL                COMMENT '关联 patient_db.patient.id（应用层引用）',
    `slot_id`          BIGINT        NOT NULL                COMMENT '关联 slot.id',
    `schedule_id`      BIGINT        NOT NULL                COMMENT '关联 schedule.id（冗余）',
    `doctor_id`        BIGINT        NOT NULL                COMMENT '关联 doctor.id（冗余）',
    `department_id`    BIGINT        NOT NULL                COMMENT '关联 department.id（冗余）',
    `appointment_date` DATE          NOT NULL                COMMENT '就诊日期（冗余）',
    `period`           VARCHAR(10)   NOT NULL                COMMENT 'AM/PM（冗余）',
    `slot_seq`         INT           NOT NULL                COMMENT '号源序号（冗余）',
    `register_fee`     DECIMAL(10,2) NOT NULL                COMMENT '挂号费（冗余）',
    `order_status`     VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY-待支付 / PAID-已支付 / CANCELLED-已取消 / TIMEOUT-已超时 / REFUNDED-已退款',
    `visit_status`     VARCHAR(20)   DEFAULT NULL            COMMENT 'CHECKED_IN-已签到 / WAITING-排队中 / CALLED-已叫号 / IN_PROGRESS-就诊中 / COMPLETED-已完成 / MISSED-过号',
    `is_revisit`       TINYINT       DEFAULT 0               COMMENT '是否复诊：0-初诊 1-复诊',
    `cancel_time`      DATETIME      DEFAULT NULL            COMMENT '取消时间',
    `cancel_reason`    VARCHAR(255)  DEFAULT NULL            COMMENT '取消原因',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_appointment_no` (`appointment_no`),
    KEY `idx_appt_patient` (`patient_id`),
    KEY `idx_appt_slot` (`slot_id`),
    KEY `idx_appt_schedule` (`schedule_id`),
    KEY `idx_appt_order_status` (`order_status`),
    KEY `idx_appt_date_dept` (`appointment_date`, `department_id`),
    KEY `idx_appt_doctor_date` (`doctor_id`, `appointment_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预约订单表';

-- ==================== 6. checkin（签到记录表） ====================
CREATE TABLE `checkin` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `appointment_id` BIGINT      NOT NULL                COMMENT '关联 appointment.id（一对一）',
    `patient_id`     BIGINT      NOT NULL                COMMENT '关联 patient_db.patient.id',
    `department_id`  BIGINT      NOT NULL                COMMENT '科室ID（冗余）',
    `doctor_id`      BIGINT      NOT NULL                COMMENT '医生ID（冗余）',
    `checkin_time`   DATETIME    NOT NULL                COMMENT '签到时间',
    `queue_status`   VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING-等待中 / CALLED-已叫号 / RE_CALLED-已重呼 / MISSED-过号 / IN_CONSULT-就诊中',
    `call_time`      DATETIME    DEFAULT NULL            COMMENT '最近一次叫号时间',
    `call_count`     INT         DEFAULT 0               COMMENT '叫号次数（含重呼）',
    `rejoin_time`    DATETIME    DEFAULT NULL            COMMENT '过号后重新排队时间',
    `consult_room`   VARCHAR(50) DEFAULT NULL            COMMENT '诊室号',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_checkin_appointment` (`appointment_id`),
    KEY `idx_checkin_dept_status` (`department_id`, `queue_status`),
    KEY `idx_checkin_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签到记录表';

-- ==================== 7. medical_record（病历表） ====================
CREATE TABLE `medical_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `appointment_id`  BIGINT       NOT NULL                COMMENT '关联 appointment.id',
    `patient_id`      BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `doctor_id`       BIGINT       NOT NULL                COMMENT '关联 doctor.id',
    `department_id`   BIGINT       NOT NULL                COMMENT '科室ID（冗余）',
    `chief_complaint` TEXT         DEFAULT NULL            COMMENT '主诉',
    `present_illness` TEXT         DEFAULT NULL            COMMENT '现病史',
    `past_history`    TEXT         DEFAULT NULL            COMMENT '既往史',
    `temperature`     DECIMAL(4,1) DEFAULT NULL            COMMENT '体温（℃）',
    `pulse`           INT          DEFAULT NULL            COMMENT '脉搏（次/分）',
    `respiration`     INT          DEFAULT NULL            COMMENT '呼吸（次/分）',
    `blood_pressure`  VARCHAR(20)  DEFAULT NULL            COMMENT '血压（如 120/80）',
    `diagnosis_code`  VARCHAR(20)  DEFAULT NULL            COMMENT '初步诊断 ICD-10 编码',
    `diagnosis_desc`  VARCHAR(500) NOT NULL                COMMENT '诊断描述',
    `treatment_opinion` TEXT       DEFAULT NULL            COMMENT '处理意见',
    `referral_dept_id` BIGINT      DEFAULT NULL            COMMENT '转诊目标科室ID',
    `referral_reason` VARCHAR(500) DEFAULT NULL            COMMENT '转诊原因',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT-草稿 / SUBMITTED-已提交 / COMPLETED-已完成',
    `is_return_visit` TINYINT      DEFAULT 0               COMMENT '是否回诊病历',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mr_appointment` (`appointment_id`),
    KEY `idx_mr_patient` (`patient_id`),
    KEY `idx_mr_doctor` (`doctor_id`),
    KEY `idx_mr_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='病历表';

-- ==================== 8. prescription（处方主表） ====================
CREATE TABLE `prescription` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `prescription_no`   VARCHAR(32)  NOT NULL                COMMENT '处方编号',
    `medical_record_id` BIGINT       NOT NULL                COMMENT '关联 medical_record.id',
    `patient_id`        BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id（冗余）',
    `doctor_id`         BIGINT       NOT NULL                COMMENT '关联 doctor.id（冗余）',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW-待审核 / REVIEW_PASSED-审核通过 / REVIEW_REJECTED-审核驳回 / DISPENSED-已发药',
    `review_comment`    VARCHAR(500) DEFAULT NULL            COMMENT '审核意见',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prescription_no` (`prescription_no`),
    KEY `idx_prescr_mr` (`medical_record_id`),
    KEY `idx_prescr_patient` (`patient_id`),
    KEY `idx_prescr_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='处方主表';

-- ==================== 9. prescription_item（处方明细表） ====================
CREATE TABLE `prescription_item` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `prescription_id` BIGINT       NOT NULL                COMMENT '关联 prescription.id',
    `drug_id`         BIGINT       NOT NULL                COMMENT '关联 medsupply_db.drug.id（应用层引用）',
    `drug_name`       VARCHAR(200) NOT NULL                COMMENT '药品名称（冗余）',
    `specification`   VARCHAR(100) DEFAULT NULL            COMMENT '规格',
    `dosage`          VARCHAR(50)  NOT NULL                COMMENT '单次用量',
    `usage_method`    VARCHAR(50)  NOT NULL                COMMENT '用法：ORAL-口服 / EXTERNAL-外用 / IV-静脉注射 / IM-肌肉注射',
    `frequency`       VARCHAR(50)  NOT NULL                COMMENT '频次：QD-每日1次 / BID-每日2次 / TID-每日3次 / QN-睡前',
    `days`            INT          NOT NULL                COMMENT '用药天数',
    `quantity`        INT          NOT NULL                COMMENT '总量',
    `unit`            VARCHAR(20)  NOT NULL                COMMENT '单位：TABLET-片 / VIAL-支 / BOTTLE-瓶 / BOX-盒',
    `remark`          VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_prescr_item` (`prescription_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='处方明细表';

-- ==================== 10. stop_application（停诊申请表） ====================
CREATE TABLE `stop_application` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `schedule_id`     BIGINT        NOT NULL                COMMENT '关联 schedule.id',
    `doctor_id`       BIGINT        NOT NULL                COMMENT '关联 doctor.id（申请医生）',
    `apply_reason`    VARCHAR(500)  NOT NULL                COMMENT '停诊原因',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待审批 / APPROVED-已通过 / REJECTED-已驳回',
    `approve_comment` VARCHAR(500)  DEFAULT NULL            COMMENT '审批意见',
    `approved_by`     BIGINT        DEFAULT NULL            COMMENT '审批人ID（关联 auth_db.user.id）',
    `approve_time`    DATETIME      DEFAULT NULL            COMMENT '审批时间',
    `affected_count`  INT           DEFAULT 0               COMMENT '受影响的待签到预约数',
    `refund_total`    DECIMAL(10,2) DEFAULT 0.00            COMMENT '退款总金额',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_stop_status` (`status`),
    KEY `idx_stop_doctor` (`doctor_id`),
    KEY `idx_stop_schedule` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='停诊申请表';

-- ==================== 初始数据：14 个科室 ====================
INSERT INTO `department` (`dept_name`, `dept_code`, `description`, `sort_order`) VALUES
('内科',       'INTERNAL_MEDICINE',        '内科疾病诊疗',                 1),
('外科',       'SURGERY',                  '外科疾病及手术诊疗',           2),
('儿科',       'PEDIATRICS',               '儿童疾病诊疗',                 3),
('妇产科',     'OBSTETRICS_GYNECOLOGY',    '妇产科疾病诊疗',               4),
('骨科',       'ORTHOPEDICS',              '骨骼、关节及运动系统疾病',     5),
('眼科',       'OPHTHALMOLOGY',            '眼部疾病诊疗',                 6),
('耳鼻喉科',   'ENT',                      '耳鼻喉及头颈外科疾病',         7),
('皮肤科',     'DERMATOLOGY',              '皮肤疾病诊疗',                 8),
('神经内科',   'NEUROLOGY',                '神经系统疾病诊疗',             9),
('心内科',     'CARDIOLOGY',               '心血管系统疾病诊疗',          10),
('呼吸内科',   'RESPIRATORY',              '呼吸系统疾病诊疗',            11),
('消化内科',   'GASTROENTEROLOGY',         '消化系统疾病诊疗',            12),
('内分泌科',   'ENDOCRINOLOGY',            '内分泌及代谢疾病诊疗',        13),
('口腔科',     'STOMATOLOGY',              '口腔及牙科疾病诊疗',          14);
