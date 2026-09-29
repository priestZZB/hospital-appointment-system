-- ============================================================
-- clinic_db V4：排班审批 + 停诊两级审批（Oracle 版）
-- 说明：对齐真实医院业务
--   1. schedule 新增 audit_status 字段（排班审批状态）
--      PENDING-待门诊部确认（未生成号源）/ CONFIRMED-已确认（已生成号源，可挂号）/ REJECTED-已驳回
--      存量排班（status=1）统一置为 CONFIRMED，保证历史数据可正常挂号
--   2. stop_application 新增科主任初审字段
-- 迁移：多列合并为一条 ALTER ... ADD (...)；Oracle 无 AFTER 子句
-- ============================================================

-- 1. schedule 增加排班审批状态字段
ALTER TABLE schedule
    ADD (audit_status VARCHAR2(20 CHAR) DEFAULT 'PENDING' NOT NULL);

-- 存量已生效排班（status=1）视为已确认，避免历史数据不可挂号
UPDATE schedule SET audit_status = 'CONFIRMED' WHERE status = 1;

-- 2. stop_application 增加科主任初审字段
ALTER TABLE stop_application
    ADD (chief_review_status  VARCHAR2(20 CHAR)  DEFAULT NULL,
         chief_reviewed_by    NUMBER(19)         DEFAULT NULL,
         chief_review_comment VARCHAR2(500 CHAR) DEFAULT NULL,
         chief_review_time    TIMESTAMP          DEFAULT NULL);

-- 存量停诊申请：状态语义调整（PENDING → 待科主任初审）
UPDATE stop_application SET chief_review_status = 'PENDING_CHIEF' WHERE status = 'PENDING';
UPDATE stop_application SET chief_review_status = 'CHIEF_PASSED' WHERE status IN ('APPROVED', 'REJECTED');
