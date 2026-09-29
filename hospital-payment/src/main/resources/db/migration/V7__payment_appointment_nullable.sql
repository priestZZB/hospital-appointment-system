-- =============================================================
-- V7: payment_order.appointment_id 撤销 NOT NULL 约束
-- =============================================================
-- 背景：V5 使用 "MODIFY (appointment_id NUMBER(19) DEFAULT NULL)" 仅
--       修改了列默认值，Oracle 中该语法不会撤销既有 NOT NULL 约束，
--       导致诊疗费/输液费/收费员代建单（appointment_id 为 NULL 的
--       TREATMENT 订单）插入时 ORA-01400 失败。
-- 本迁移显式置空约束（对已可空列执行为幂等无操作）。

ALTER TABLE payment_order MODIFY (appointment_id NULL);
