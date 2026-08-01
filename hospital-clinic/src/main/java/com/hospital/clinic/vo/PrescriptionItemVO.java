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
    private String unit;
    private String remark;
}
