package com.hospital.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 分诊返回结果 VO
 */
@Data
@Builder
public class TriageResultVO {

    /** 推荐科室 ID */
    private Long deptId;

    /** 推荐科室名称 */
    private String deptName;

    /** 置信度 0.00 ~ 100.00 */
    private BigDecimal confidence;

    /** 是否走了降级路径 */
    private Boolean isDegraded;

    /** 降级原因（仅降级时有值） */
    private String degradedReason;

    /** 执行耗时（毫秒） */
    private Long executionTimeMs;
}
