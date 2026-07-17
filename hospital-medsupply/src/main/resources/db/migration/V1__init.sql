-- ============================================================
-- medsupply_db V1__init.sql
-- 医辅物资服务：7 张表
-- ============================================================

-- ==================== 1. drug（药品目录表） ====================
CREATE TABLE `drug` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `drug_code`       VARCHAR(50)   NOT NULL                COMMENT '药品编码',
    `drug_name`       VARCHAR(200)  NOT NULL                COMMENT '药品名称（商品名）',
    `generic_name`    VARCHAR(200)  DEFAULT NULL            COMMENT '通用名',
    `specification`   VARCHAR(100)  DEFAULT NULL            COMMENT '规格',
    `dosage_form`     VARCHAR(50)   DEFAULT NULL            COMMENT '剂型：TABLET-片剂 / CAPSULE-胶囊 / INJECTION-注射液 / SYRUP-糖浆',
    `manufacturer`    VARCHAR(200)  DEFAULT NULL            COMMENT '生产厂家',
    `reference_price` DECIMAL(10,2) DEFAULT 0.00            COMMENT '参考价格',
    `unit`            VARCHAR(20)   DEFAULT NULL            COMMENT '单位',
    `description`     TEXT          DEFAULT NULL            COMMENT '药品描述',
    `status`          TINYINT       DEFAULT 1               COMMENT '1-启用 0-停用',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drug_code` (`drug_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='药品目录表';

-- ==================== 2. drug_inventory（药品库存表） ====================
CREATE TABLE `drug_inventory` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `drug_id`           BIGINT NOT NULL                COMMENT '关联 drug.id',
    `current_stock`     INT    DEFAULT 0               COMMENT '当前库存量',
    `min_threshold`     INT    DEFAULT 10              COMMENT '库存最低阈值',
    `version`           INT    DEFAULT 0               COMMENT '乐观锁版本号',
    `last_stockin_time` DATETIME DEFAULT NULL          COMMENT '最近入库时间',
    `last_stockout_time` DATETIME DEFAULT NULL         COMMENT '最近出库时间',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_drug` (`drug_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='药品库存表';

-- ==================== 3. drug_inventory_log（库存流水表） ====================
CREATE TABLE `drug_inventory_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `drug_id`          BIGINT       NOT NULL                COMMENT '关联 drug.id',
    `change_type`      VARCHAR(20)  NOT NULL                COMMENT 'IN-入库 / OUT-出库 / ADJUST-盘点调整 / DISPENSE-发药扣减',
    `change_quantity`  INT          NOT NULL                COMMENT '变更数量（正数为入库，负数为出库）',
    `before_stock`     INT          NOT NULL                COMMENT '变更前库存',
    `after_stock`      INT          NOT NULL                COMMENT '变更后库存',
    `related_order_id` BIGINT       DEFAULT NULL            COMMENT '关联单据ID',
    `operator_id`      BIGINT       DEFAULT NULL            COMMENT '操作人ID',
    `remark`           VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_inv_log_drug` (`drug_id`),
    KEY `idx_inv_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存流水表';

-- ==================== 4. exam_item（检查检验项目表） ====================
CREATE TABLE `exam_item` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_code`       VARCHAR(50)   NOT NULL                COMMENT '项目编码',
    `item_name`       VARCHAR(200)  NOT NULL                COMMENT '项目名称',
    `item_type`       VARCHAR(50)   NOT NULL                COMMENT '项目类型：LAB-检验 / RADIOLOGY-放射 / ULTRASOUND-超声 / ENDOSCOPY-内镜 / ECG-心电',
    `reference_price` DECIMAL(10,2) DEFAULT 0.00            COMMENT '参考价格',
    `exec_dept`       VARCHAR(100)  DEFAULT NULL            COMMENT '执行科室',
    `precautions`     TEXT          DEFAULT NULL            COMMENT '注意事项',
    `status`          TINYINT       DEFAULT 1               COMMENT '1-启用 0-停用',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_item_code` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检查检验项目表';

-- ==================== 5. exam_application（检查申请表） ====================
CREATE TABLE `exam_application` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `application_no`    VARCHAR(32)  NOT NULL                COMMENT '申请编号',
    `medical_record_id` BIGINT       NOT NULL                COMMENT '关联 clinic_db.medical_record.id（应用层引用）',
    `patient_id`        BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `doctor_id`         BIGINT       NOT NULL                COMMENT '关联 clinic_db.doctor.id',
    `exam_item_id`      BIGINT       NOT NULL                COMMENT '关联 exam_item.id',
    `exam_item_name`    VARCHAR(200) NOT NULL                COMMENT '检查项目名称（冗余）',
    `item_type`         VARCHAR(50)  NOT NULL                COMMENT '项目类型（冗余）',
    `apply_remark`      VARCHAR(500) DEFAULT NULL            COMMENT '申请备注',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待执行 / EXECUTING-执行中 / COMPLETED-已完成 / CANCELLED-已取消',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_application_no` (`application_no`),
    KEY `idx_exam_app_patient` (`patient_id`),
    KEY `idx_exam_app_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检查申请表';

-- ==================== 6. exam_report（检查报告表） ====================
CREATE TABLE `exam_report` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `application_id`  BIGINT       NOT NULL                COMMENT '关联 exam_application.id',
    `patient_id`      BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `report_desc`     TEXT         DEFAULT NULL            COMMENT '检查描述',
    `report_result`   TEXT         DEFAULT NULL            COMMENT '检查结果/诊断',
    `attachment_url`  VARCHAR(500) DEFAULT NULL            COMMENT '报告附件 MinIO URL',
    `attachment_name` VARCHAR(200) DEFAULT NULL            COMMENT '附件文件名',
    `operator_id`     BIGINT       DEFAULT NULL            COMMENT '报告录入人ID',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT-草稿 / PUBLISHED-已发布',
    `complete_time`   DATETIME     DEFAULT NULL            COMMENT '报告完成时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_report_application` (`application_id`),
    KEY `idx_exam_report_patient` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检查报告表';

-- ==================== 7. drug_dispense（发药记录表） ====================
CREATE TABLE `drug_dispense` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `prescription_id`      BIGINT       NOT NULL                COMMENT '关联 clinic_db.prescription.id（应用层引用）',
    `patient_id`           BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `status`               VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW-待审核 / REVIEW_PASSED-审核通过 / REVIEW_REJECTED-审核驳回 / DISPENSED-已发药',
    `review_operator_id`   BIGINT       DEFAULT NULL            COMMENT '审核人ID',
    `review_comment`       VARCHAR(500) DEFAULT NULL            COMMENT '审核意见',
    `review_time`          DATETIME     DEFAULT NULL            COMMENT '审核时间',
    `dispense_operator_id` BIGINT       DEFAULT NULL            COMMENT '发药人ID',
    `dispense_time`        DATETIME     DEFAULT NULL            COMMENT '发药时间',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dispense_prescription` (`prescription_id`),
    KEY `idx_dispense_patient` (`patient_id`),
    KEY `idx_dispense_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='发药记录表';
