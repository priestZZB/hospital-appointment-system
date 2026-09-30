package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 处方明细 VO
 */
@Data
@Builder
public class PrescriptionItemVO {

    private Long id;
    private Long prescriptionId;
    private Long drugId;
    private String drugName;
    private String specification;
    private String dosage;
    private String usageMethod;
    private String frequency;
    private Integer days;
    private Integer quantity;
    private java.math.BigDecimal unitPrice;
    private String unit;
    private String remark;
    /** 中药煎法：先煎/后下/包煎/烊化等（HERBAL 明细使用，V9） */
    private String decoctionMethod;
    /** 中药脚注：特殊处理说明（HERBAL 明细使用，V9） */
    private String footnote;
}
