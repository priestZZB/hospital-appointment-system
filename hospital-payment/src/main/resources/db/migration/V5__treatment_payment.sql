-- ============================================================
-- payment_db V5：诊疗费二次缴费（明细计价）
-- 说明：
--   1. payment_order 增加 related_id（关联处方/检查/输液单ID）+ item_detail（明细计价JSON）
--   2. appointment_id 改为可空（诊疗费订单不关联单个预约）
--   3. order_type 扩展：新增 INFUSION-输液费 / TREATMENT-诊疗费
-- ============================================================

-- 1. appointment_id 改为可空（诊疗费订单关联处方/检查/输液单，而非单个预约）
ALTER TABLE `payment_order`
    MODIFY COLUMN `appointment_id` BIGINT DEFAULT NULL COMMENT '关联 clinic_db.appointment.id（挂号费订单必填，诊疗费订单可空）';

-- 2. 新增关联业务ID + 明细计价字段
ALTER TABLE `payment_order`
    ADD COLUMN `related_id` BIGINT DEFAULT NULL COMMENT '关联业务ID（处方/检查申请/输液单ID）' AFTER `appointment_id`,
    ADD COLUMN `item_detail` TEXT DEFAULT NULL COMMENT '明细计价JSON（诊疗费订单用，如[{"itemName":"阿莫西林","qty":1,"price":15.00}...]）' AFTER `amount`;

-- 3. order_type 注释扩展（字段本身已存在，无需改列，仅说明）
-- REGISTRATION-挂号费 / DRUG-药品费 / EXAM-检查费 / INFUSION-输液费 / TREATMENT-诊疗费汇总
