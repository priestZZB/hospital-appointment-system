-- ============================================================
-- inpatient_db V1__init.sql
-- 住院服务：入院/床位/占床/医嘱/执行/生命体征/预交金/出院
-- 时间戳留痕（供后续算法层/AI 素材）
-- ============================================================

-- 1. admission（入院登记主表）
CREATE TABLE `admission` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_no`      VARCHAR(32)  NOT NULL                COMMENT '住院号（唯一，病历号体系）',
    `patient_id`        BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `department_id`     BIGINT       NOT NULL                COMMENT '拟收治科室ID',
    `attending_doctor_id` BIGINT     NOT NULL                COMMENT '主治医生用户ID',
    `attending_doctor_name` VARCHAR(50) DEFAULT NULL          COMMENT '主治医生姓名（冗余）',
    `admission_diag`    VARCHAR(500) NOT NULL                COMMENT '入院诊断',
    `expected_days`     INT          DEFAULT NULL            COMMENT '预计住院天数',
    `admission_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入院时间',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'ADMITTED' COMMENT 'ADMITTED-在院 / DISCHARGED-已出院 / TRANSFERRED-已转科',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admission_no` (`admission_no`),
    KEY `idx_admission_patient` (`patient_id`),
    KEY `idx_admission_dept` (`department_id`),
    KEY `idx_admission_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入院登记表';

-- 2. bed（床位字典）
CREATE TABLE `bed` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `department_id` BIGINT      NOT NULL                COMMENT '科室ID',
    `room_no`       VARCHAR(20) NOT NULL                COMMENT '房间号',
    `bed_no`        VARCHAR(20) NOT NULL                COMMENT '床位号',
    `bed_type`      VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL-普通 / ICU-重症 / ISOLATION-隔离',
    `daily_fee`     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '床位费/日',
    `status`        VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE-空闲 / OCCUPIED-占用 / MAINTENANCE-维修',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bed_dept_room_no` (`department_id`, `room_no`, `bed_no`),
    KEY `idx_bed_dept` (`department_id`),
    KEY `idx_bed_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='床位字典表';

-- 3. bed_occupancy（占床记录，含转床历史）
CREATE TABLE `bed_occupancy` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`   BIGINT   NOT NULL                COMMENT '关联 admission.id',
    `bed_id`         BIGINT   NOT NULL                COMMENT '关联 bed.id',
    `start_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入住时间',
    `end_time`       DATETIME DEFAULT NULL              COMMENT '退房/转床时间',
    `change_type`    VARCHAR(20) NOT NULL DEFAULT 'ADMIT' COMMENT 'ADMIT-入院 / TRANSFER-转床 / DISCHARGE-出院',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_occupancy_admission` (`admission_id`),
    KEY `idx_occupancy_bed` (`bed_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='占床记录表';

-- 4. inpatient_medical_order（住院医嘱：长期/临时）
CREATE TABLE `inpatient_medical_order` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`           VARCHAR(32)  NOT NULL                COMMENT '医嘱编号',
    `admission_id`       BIGINT       NOT NULL                COMMENT '关联 admission.id',
    `doctor_id`          BIGINT       NOT NULL                COMMENT '开立医生ID',
    `order_type`         VARCHAR(20)  NOT NULL DEFAULT 'TEMPORARY' COMMENT 'LONG_TERM-长期 / TEMPORARY-临时',
    `category`           VARCHAR(30)  NOT NULL                COMMENT 'DRUG-药品 / EXAM-检查 / LAB-检验 / INFUSION-输液 / NURSING-护理 / DIET-饮食 / OTHER-其他',
    `content`            VARCHAR(1000) NOT NULL               COMMENT '医嘱内容',
    `frequency`          VARCHAR(20)  DEFAULT NULL            COMMENT '频次：QD/BID/TID/QN/PRN/STAT',
    `status`             VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN-开立 / CONFIRMED-已核对 / EXECUTING-执行中 / COMPLETED-已完成 / STOPPED-已停止',
    `open_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开立时间',
    `confirm_time`       DATETIME     DEFAULT NULL            COMMENT '核对时间',
    `confirm_nurse_id`   BIGINT       DEFAULT NULL            COMMENT '核对护士ID',
    `stop_time`          DATETIME     DEFAULT NULL            COMMENT '停止时间',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_order_admission` (`admission_id`),
    KEY `idx_order_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='住院医嘱表';

-- 5. order_execution（医嘱执行记录：执行人/时间/结果）
CREATE TABLE `order_execution` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`       BIGINT       NOT NULL                COMMENT '关联 inpatient_medical_order.id',
    `admission_id`   BIGINT       NOT NULL                COMMENT '关联 admission.id（冗余便于查询）',
    `executor_id`    BIGINT       NOT NULL                COMMENT '执行护士ID',
    `execute_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    `result`         VARCHAR(500) DEFAULT NULL            COMMENT '执行结果/备注',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_exec_order` (`order_id`),
    KEY `idx_exec_admission` (`admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='医嘱执行记录表';

-- 6. vital_sign（生命体征：多时段）
CREATE TABLE `vital_sign` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`  BIGINT      NOT NULL                COMMENT '关联 admission.id',
    `temperature`   DECIMAL(4,1) DEFAULT NULL           COMMENT '体温℃',
    `pulse`         INT          DEFAULT NULL           COMMENT '脉搏',
    `respiration`   INT          DEFAULT NULL           COMMENT '呼吸',
    `blood_pressure` VARCHAR(20) DEFAULT NULL           COMMENT '血压',
    `blood_oxygen`  INT          DEFAULT NULL           COMMENT '血氧饱和度%',
    `record_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间（多时段）',
    `operator_id`   BIGINT       DEFAULT NULL           COMMENT '记录护士ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_vital_admission_time` (`admission_id`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生命体征表';

-- 7. deposit（预交金流水）
CREATE TABLE `deposit` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`   BIGINT      NOT NULL                COMMENT '关联 admission.id',
    `amount`         DECIMAL(10,2) NOT NULL              COMMENT '交款金额',
    `pay_method`     VARCHAR(20) NOT NULL DEFAULT 'CASH' COMMENT 'CASH/WECHAT/ALIPAY/CARD/MEDICARE',
    `balance_after`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '交款后余额',
    `operator_id`    BIGINT      DEFAULT NULL            COMMENT '收费员ID',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_deposit_admission` (`admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预交金流水表';

-- 8. inpatient_fee（住院费用流水：床位/诊疗/药品/检查检验）
CREATE TABLE `inpatient_fee` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`  BIGINT      NOT NULL                COMMENT '关联 admission.id',
    `fee_type`      VARCHAR(20) NOT NULL                COMMENT 'BED-床位 / TREATMENT-诊疗 / DRUG-药品 / EXAM-检查 / LAB-检验 / NURSING-护理 / OTHER-其他',
    `item_name`     VARCHAR(200) NOT NULL               COMMENT '项目名称',
    `amount`        DECIMAL(10,2) NOT NULL              COMMENT '金额',
    `bill_date`     DATE        NOT NULL                COMMENT '账单日期',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_fee_admission_date` (`admission_id`, `bill_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='住院费用流水表';

-- 9. discharge_summary（出院小结）
CREATE TABLE `discharge_summary` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admission_id`     BIGINT      NOT NULL                COMMENT '关联 admission.id',
    `admission_diag`   VARCHAR(500) NOT NULL               COMMENT '入院诊断',
    `discharge_diag`   VARCHAR(500) NOT NULL               COMMENT '出院诊断',
    `treatment_process` TEXT       DEFAULT NULL           COMMENT '诊疗过程',
    `discharge_condition` VARCHAR(500) DEFAULT NULL       COMMENT '出院情况',
    `discharge_advice` TEXT       DEFAULT NULL            COMMENT '出院医嘱/建议',
    `doctor_id`        BIGINT      NOT NULL                COMMENT '书写医生ID',
    `settlement_amount` DECIMAL(10,2) DEFAULT 0.00        COMMENT '出院结算金额',
    `deposit_balance`  DECIMAL(10,2) DEFAULT 0.00         COMMENT '预交金余额（多退少补）',
    `discharge_time`   DATETIME    DEFAULT NULL            COMMENT '出院时间',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_discharge_admission` (`admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出院小结表';

-- 床位种子：14 个临床科室各 6 张（2 间病房），含 1 张 ICU
INSERT INTO `bed` (`department_id`, `room_no`, `bed_no`, `bed_type`, `daily_fee`)
SELECT d.dept_id, b.room_no, b.bed_no,
       CASE WHEN b.room_no = '201' AND b.bed_no = '1' THEN 'ICU' ELSE 'NORMAL' END,
       CASE WHEN b.room_no = '201' AND b.bed_no = '1' THEN 500.00 ELSE 80.00 END
FROM (
    SELECT 1 AS dept_id UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
    SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
    SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL
    SELECT 13 UNION ALL SELECT 14
) d
CROSS JOIN (
    SELECT '101' AS room_no, '1' AS bed_no UNION ALL
    SELECT '101','2' UNION ALL SELECT '101','3' UNION ALL
    SELECT '201','1' UNION ALL SELECT '201','2' UNION ALL SELECT '201','3'
) b;
