-- ============================================================
-- clinic_db V5：处方缴费状态（二次缴费回写闭环）
-- 说明：
--   1. prescription 增加 total_amount（实收金额，缴费回写时写入）
--   2. prescription 增加 pay_status（UNPAID-未缴费 / PAID-已缴费 / REFUNDED-已退款）
--   3. 药师发药需校验 pay_status = PAID
-- ============================================================

ALTER TABLE `prescription`
    ADD COLUMN `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '处方总金额（实收金额，缴费回写时写入）' AFTER `review_comment`,
    ADD COLUMN `pay_status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '缴费状态：UNPAID-未缴费 / PAID-已缴费 / REFUNDED-已退款' AFTER `total_amount`;

-- 为存量已发药处方补缴费状态（历史数据按已缴费处理，避免发药门控误伤）
UPDATE `prescription` SET `pay_status` = 'PAID' WHERE `status` IN ('DISPENSED', 'REVIEW_PASSED');
