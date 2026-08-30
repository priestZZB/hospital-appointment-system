-- ============================================================
-- clinic_db V4：排班审批 + 停诊两级审批
-- 说明：对齐真实医院业务
--   1. schedule 新增 audit_status 字段（排班审批状态）
--      PENDING-待门诊部确认（未生成号源）/ CONFIRMED-已确认（已生成号源，可挂号）/ REJECTED-已驳回
--      存量排班（status=1）统一置为 CONFIRMED，保证历史数据可正常挂号
--   2. stop_application 新增科主任初审字段
-- ============================================================

-- 1. schedule 增加排班审批状态字段
ALTER TABLE `schedule`
    ADD COLUMN `audit_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT '审批状态：PENDING-待确认 / CONFIRMED-已确认 / REJECTED-已驳回' AFTER `status`;

-- 存量已生效排班（status=1）视为已确认，避免历史数据不可挂号
UPDATE `schedule` SET `audit_status` = 'CONFIRMED' WHERE `status` = 1;

-- 2. stop_application 增加科主任初审字段
ALTER TABLE `stop_application`
    ADD COLUMN `chief_review_status` VARCHAR(20) DEFAULT NULL
        COMMENT '科主任初审：PENDING_CHIEF-待初审 / CHIEF_PASSED-初审通过 / CHIEF_REJECTED-初审驳回' AFTER `status`,
    ADD COLUMN `chief_reviewed_by` BIGINT DEFAULT NULL
        COMMENT '科主任初审人ID（关联 auth_db.user.id）' AFTER `chief_review_status`,
    ADD COLUMN `chief_review_comment` VARCHAR(500) DEFAULT NULL
        COMMENT '科主任初审意见' AFTER `chief_reviewed_by`,
    ADD COLUMN `chief_review_time` DATETIME DEFAULT NULL
        COMMENT '科主任初审时间' AFTER `chief_review_comment`;

-- 存量停诊申请：状态语义调整（PENDING → 待科主任初审）
UPDATE `stop_application` SET `chief_review_status` = 'PENDING_CHIEF' WHERE `status` = 'PENDING';
UPDATE `stop_application` SET `chief_review_status` = 'CHIEF_PASSED' WHERE `status` IN ('APPROVED', 'REJECTED');
