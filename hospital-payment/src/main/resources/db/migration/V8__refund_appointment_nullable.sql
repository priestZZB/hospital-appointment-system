-- =============================================================
-- V8: refund_record.appointment_id 撤销 NOT NULL 约束
-- =============================================================
-- 背景：诊疗费/输液费/收费员代建单退款时，源订单 payment_order.appointment_id
--       为 NULL（见 V7），refund_record 同步插入退款记录时 appointment_id
--       沿用 NULL，触发 ORA-01400。
-- 本迁移显式置空约束（对已可空列执行为幂等无操作）。

ALTER TABLE refund_record MODIFY (appointment_id NULL);
