-- ============================================================
-- payment_db V6：收费员代缴费 + 诊疗费退费 + 日结对账
-- 说明：对齐真实医院收费处业务
--   1. refund_record 的 appointment_id 改为可空（诊疗费退费不关联预约），新增 order_type
--   2. payment_order 新增 cashier_id（收费员代缴费记录）、settle_flag（日结标记）
--   3. 新增 settle_record（收费员日结单表）
-- ============================================================

-- 1. 退款记录：appointment_id 可空 + 新增 order_type
ALTER TABLE `refund_record`
    MODIFY COLUMN `appointment_id` BIGINT DEFAULT NULL COMMENT '关联 clinic_db.appointment.id（挂号退费用，诊疗退费可空）',
    ADD COLUMN `order_type` VARCHAR(20) DEFAULT NULL COMMENT '订单类型：REGISTRATION/DRUG/EXAM/INFUSION/TREATMENT' AFTER `refund_type`;

-- 2. 支付订单：收费员代缴费记录 + 日结标记
ALTER TABLE `payment_order`
    ADD COLUMN `cashier_id` BIGINT DEFAULT NULL COMMENT '收费员ID（收费员代缴费时记录）' AFTER `pay_method`,
    ADD COLUMN `settle_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '日结标记：0-未结 1-已结' AFTER `cashier_id`;

-- 3. 收费员日结单表（同一收费员 + 日期唯一，幂等）
CREATE TABLE `settle_record` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `settle_no`    VARCHAR(32)   NOT NULL                COMMENT '日结单编号',
    `cashier_id`   BIGINT        NOT NULL                COMMENT '收费员ID（关联 auth_db.user.id）',
    `settle_date`  DATE          NOT NULL                COMMENT '结算日期',
    `total_amount` DECIMAL(10,2) NOT NULL                COMMENT '结算总金额',
    `order_count`  INT           NOT NULL                COMMENT '订单笔数',
    `detail`       TEXT          DEFAULT NULL            COMMENT '分类汇总JSON（按订单类型/支付方式）',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settle_cashier_date` (`cashier_id`, `settle_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收费员日结单表';
