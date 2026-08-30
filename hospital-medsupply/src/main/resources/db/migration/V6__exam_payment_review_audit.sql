-- ============================================================
-- medsupply_db V6：检查缴费回写 + 四查十对审核 + 报告审核
-- 说明：对齐真实医院门诊业务
--   1. exam_application 增加 total_amount / pay_status（二次缴费回写闭环）
--   2. drug_dispense 增加 review_check（药师四查十对核查结果）
--   3. exam_report 增加审核字段（报告草稿 → 审核发布）
-- ============================================================

-- 1. 检查申请：缴费金额 + 缴费状态
ALTER TABLE `exam_application`
    ADD COLUMN `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '检查费金额（实收金额，缴费回写时写入）' AFTER `apply_remark`,
    ADD COLUMN `pay_status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '缴费状态：UNPAID-未缴费 / PAID-已缴费 / REFUNDED-已退款' AFTER `total_amount`;

-- 2. 发药记录：四查十对核查结果（JSON，如 [{"item":"查处方","result":"PASS","remark":"..."}...]）
ALTER TABLE `drug_dispense`
    ADD COLUMN `review_check` TEXT DEFAULT NULL COMMENT '四查十对核查结果JSON' AFTER `review_comment`;

-- 3. 检查报告：报告审核字段（技师录入草稿 → 审核发布）
ALTER TABLE `exam_report`
    ADD COLUMN `auditor_id` BIGINT DEFAULT NULL COMMENT '报告审核人ID' AFTER `operator_id`,
    ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '报告审核时间' AFTER `auditor_id`,
    ADD COLUMN `audit_comment` VARCHAR(500) DEFAULT NULL COMMENT '审核意见' AFTER `audit_time`;

-- 为存量已完成检查申请补缴费状态（历史数据按已缴费处理，避免执行门控误伤）
UPDATE `exam_application` SET `pay_status` = 'PAID' WHERE `status` = 'COMPLETED';
