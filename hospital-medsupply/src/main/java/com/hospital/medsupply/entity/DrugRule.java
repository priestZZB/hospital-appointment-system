package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CDSS 合理用药规则实体
 * <p>
 * 剂量上限/重复用药/配伍禁忌/妊娠禁忌四类规则，开方时由内部接口实时校验。
 *
 * @see V8__pharmacy_extension.sql — drug_rule 表 DDL
 */
@Data
public class DrugRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 规则类型: MAX_DOSE-剂量上限 / DRUG_DUPLICATE-重复用药 / DRUG_CONFLICT-配伍禁忌 / PREGNANCY-妊娠禁忌 */
    private String ruleType;

    /** 关联 drug.id（可空，DRUG_CONFLICT 时与 paired_drug_id 成对） */
    private Long drugId;

    /** 配对药品 drug.id（DRUG_DUPLICATE/DRUG_CONFLICT 使用） */
    private Long pairedDrugId;

    /** 单次剂量上限（mg） */
    private BigDecimal maxSingleDose;

    /** 日剂量上限（mg，预留） */
    private BigDecimal maxDailyDose;

    /** 严重级别: BLOCK-拦截 / WARN-警告 */
    private String severity;

    /** 规则说明 */
    private String description;

    /** 1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
