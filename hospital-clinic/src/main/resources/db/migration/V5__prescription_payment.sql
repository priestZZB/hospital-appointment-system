-- ============================================================
-- clinic_db V5：处方缴费状态（二次缴费回写闭环）（Oracle 版）
-- 说明：
--   1. prescription 增加 total_amount（实收金额，缴费回写时写入）
--   2. prescription 增加 pay_status（UNPAID-未缴费 / PAID-已缴费 / REFUNDED-已退款）
--   3. 药师发药需校验 pay_status = PAID
-- ============================================================

ALTER TABLE prescription
    ADD (total_amount NUMBER(10,2) DEFAULT 0.00 NOT NULL,
         pay_status   VARCHAR2(20 CHAR) DEFAULT 'UNPAID' NOT NULL);

-- 为存量已发药处方补缴费状态（历史数据按已缴费处理，避免发药门控误伤）
UPDATE prescription SET pay_status = 'PAID' WHERE status IN ('DISPENSED', 'REVIEW_PASSED');
