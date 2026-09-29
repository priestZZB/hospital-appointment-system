-- ============================================================
-- clinic_db V6：处方明细划价（单价）（Oracle 版）
-- 说明：对齐真实医院「开处方 → 划价 → 缴费 → 发药」流程
--   1. prescription_item 增加 unit_price（开单时从药品参考价带入）
--   2. 处方 total_amount 在开单时按 Σ(unit_price × quantity) 计算，
--      供收费处/患者端直接收费，无需二次划价
-- ============================================================

ALTER TABLE prescription_item
    ADD (unit_price NUMBER(10,2) DEFAULT 0.00 NOT NULL);
