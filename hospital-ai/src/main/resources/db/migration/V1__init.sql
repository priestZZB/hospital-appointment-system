-- ============================================================
-- ai_db V1__init.sql
-- AI辅助服务：1 张表
-- ============================================================

-- ==================== 1. ai_call_log（AI调用日志表） ====================
CREATE TABLE `ai_call_log` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `patient_id`          BIGINT       NOT NULL                COMMENT '关联 patient_db.patient.id',
    `symptom_input`       VARCHAR(500) NOT NULL                COMMENT '症状输入文本',
    `model_name`          VARCHAR(50)  DEFAULT NULL            COMMENT '调用的 AI 模型名称',
    `request_text`        TEXT         DEFAULT NULL            COMMENT '发送给 AI 的完整 Prompt',
    `response_text`       TEXT         DEFAULT NULL            COMMENT 'AI 原始返回结果',
    `recommend_dept_id`   BIGINT       DEFAULT NULL            COMMENT '推荐的科室ID',
    `recommend_dept_name` VARCHAR(100) DEFAULT NULL            COMMENT '推荐的科室名称',
    `confidence`          DECIMAL(5,2) DEFAULT 0.00            COMMENT '置信度（0-100）',
    `is_degraded`         TINYINT      DEFAULT 0               COMMENT '是否降级到关键词匹配：0-否 1-是',
    `degraded_reason`     VARCHAR(50)  DEFAULT NULL            COMMENT '降级原因：TIMEOUT / API_ERROR',
    `execution_time_ms`   BIGINT       DEFAULT NULL            COMMENT '执行耗时（毫秒）',
    `status`              TINYINT      DEFAULT 1               COMMENT '1-成功 0-异常',
    `error_message`       VARCHAR(500) DEFAULT NULL            COMMENT '异常信息',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_call_patient` (`patient_id`),
    KEY `idx_ai_call_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI调用日志表';
