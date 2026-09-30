package com.hospital.payment.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 医保目录解析结果 VO（迭代11 H1）
 * <p>
 * 未配置映射时返回默认自费口径：catalogClass=C、reimburseRatio=0、configured=false。
 */
@Data
@Builder
public class InsuranceCatalogResolveVO {

    /** 业务类型（入参回显） */
    private String itemType;

    /** 引用业务 ID（入参回显） */
    private Long refId;

    /** 目录名称（未配置时为空） */
    private String itemName;

    /** 目录分类：A-甲类 / B-乙类 / C-自费（默认 C） */
    private String catalogClass;

    /** 比例(%)（默认 0） */
    private BigDecimal reimburseRatio;

    /** 是否已配置映射（false = 未配置走默认自费） */
    private Boolean configured;
}
