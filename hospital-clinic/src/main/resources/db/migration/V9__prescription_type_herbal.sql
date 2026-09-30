-- =============================================================
-- V9: 处方类型区分（迭代7 B4）+ 中药饮片组方明细扩展（B2）
-- =============================================================

-- B4 处方笺类型：西药处方笺 / 中药处方笺
ALTER TABLE prescription ADD (prescription_type VARCHAR2(20 CHAR) DEFAULT 'WESTERN' NOT NULL);
COMMENT ON COLUMN prescription.prescription_type IS '处方类型: WESTERN-西药/中成药处方笺 / HERBAL-中药饮片处方笺';

-- B2 中药饮片处方头：剂数与煎服法
ALTER TABLE prescription ADD (herbal_doses NUMBER(3));
COMMENT ON COLUMN prescription.herbal_doses IS '中药剂数（HERBAL 处方必填，如 7 剂）';
ALTER TABLE prescription ADD (herbal_usage VARCHAR2(100 CHAR));
COMMENT ON COLUMN prescription.herbal_usage IS '煎服法（如：每日一剂，水煎400ml，分早晚两次温服）';

-- B2 中药饮片组方明细扩展：煎法与脚注（先煎/后下/包煎/冲服等）
ALTER TABLE prescription_item ADD (decoction_method VARCHAR2(50 CHAR));
COMMENT ON COLUMN prescription_item.decoction_method IS '中药煎法（先煎/后下/包煎/烊化等，HERBAL 明细使用）';
ALTER TABLE prescription_item ADD (footnote VARCHAR2(50 CHAR));
COMMENT ON COLUMN prescription_item.footnote IS '中药脚注（特殊处理说明，HERBAL 明细使用）';
