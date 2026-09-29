-- ============================================================
-- medsupply_db V6：检查缴费回写 + 四查十对审核 + 报告审核（Oracle 19c 版）
-- 说明：对齐真实医院门诊业务
--   1. exam_application 增加 total_amount / pay_status（二次缴费回写闭环）
--   2. drug_dispense 增加 review_check（药师四查十对核查结果）
--   3. exam_report 增加审核字段（报告草稿 → 审核发布）
-- ============================================================

-- 1. 检查申请：缴费金额 + 缴费状态
ALTER TABLE exam_application
    ADD (total_amount NUMBER(10,2)      DEFAULT 0.00 NOT NULL,
         pay_status   VARCHAR2(20 CHAR) DEFAULT 'UNPAID' NOT NULL);

-- 2. 发药记录：四查十对核查结果（JSON，如 [{"item":"查处方","result":"PASS","remark":"..."}...]）
ALTER TABLE drug_dispense
    ADD (review_check CLOB);

-- 3. 检查报告：报告审核字段（技师录入草稿 → 审核发布）
ALTER TABLE exam_report
    ADD (auditor_id    NUMBER(19)         DEFAULT NULL,
         audit_time    TIMESTAMP          DEFAULT NULL,
         audit_comment VARCHAR2(500 CHAR) DEFAULT NULL);

-- 为存量已完成检查申请补缴费状态（历史数据按已缴费处理，避免执行门控误伤）
UPDATE exam_application SET pay_status = 'PAID' WHERE status = 'COMPLETED';
