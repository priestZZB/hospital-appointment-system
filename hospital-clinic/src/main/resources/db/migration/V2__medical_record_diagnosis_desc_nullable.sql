-- ============================================================
-- V2：病历表 diagnosis_desc 改为可空
-- 原因：开始接诊时创建的是 DRAFT 草稿病历，此时还没有诊断描述；
--       原 NOT NULL 约束会导致 startConsultation 插入失败（系统异常 9999）。
-- ============================================================
ALTER TABLE medical_record
    MODIFY COLUMN diagnosis_desc VARCHAR(500) NULL COMMENT '诊断描述';
