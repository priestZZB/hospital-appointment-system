package com.hospital.ai.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用日志表实体
 * <p>
 * 记录每次 AI 分诊调用的输入、输出、耗时、是否降级、模型名称等信息。
 *
 * @see V1__init.sql — ai_call_log 表 DDL
 */
@Data
public class AiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 症状输入文本 */
    private String symptomInput;

    /** 调用的 AI 模型名称 */
    private String modelName;

    /** 发送给 AI 的完整 Prompt */
    private String requestText;

    /** AI 原始返回结果 */
    private String responseText;

    /** 推荐的科室ID */
    private Long recommendDeptId;

    /** 推荐的科室名称 */
    private String recommendDeptName;

    /** 置信度（0.00 ~ 100.00） */
    private BigDecimal confidence;

    /** 是否降级到关键词匹配：0-否 1-是 */
    private Integer isDegraded;

    /** 降级原因：TIMEOUT / API_ERROR */
    private String degradedReason;

    /** 执行耗时（毫秒） */
    private Long executionTimeMs;

    /** 1-成功 0-异常 */
    private Integer status;

    /** 异常信息 */
    private String errorMessage;

    /** 调用时间 */
    private LocalDateTime createTime;
}
