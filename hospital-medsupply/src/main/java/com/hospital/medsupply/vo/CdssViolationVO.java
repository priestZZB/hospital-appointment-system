package com.hospital.medsupply.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * CDSS 合理用药校验违规项 VO
 * <p>
 * 供 {@code /api/medsupply/internal/pharmacy/cdss/check} 内部契约返回，
 * severity=BLOCK 的违规项导致整单校验不通过（passed=false）。
 */
@Data
public class CdssViolationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则类型: MAX_DOSE / DRUG_DUPLICATE / DRUG_CONFLICT / PREGNANCY */
    private String ruleType;

    /** 严重级别: BLOCK-拦截 / WARN-警告 */
    private String severity;

    /** 关联药品ID */
    private Long drugId;

    /** 药品名称 */
    private String drugName;

    /** 违规描述 */
    private String description;
}
