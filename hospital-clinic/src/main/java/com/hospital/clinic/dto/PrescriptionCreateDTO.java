package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 处方开具请求 DTO
 */
@Data
public class PrescriptionCreateDTO {

    /** 病历ID */
    @NotNull(message = "病历ID不能为空")
    private Long medicalRecordId;

    /** 处方明细 */
    private List<PrescriptionItemDTO> items;

    /**
     * 处方明细 DTO
     */
    @Data
    public static class PrescriptionItemDTO {
        private Long drugId;
        private String drugName;
        private String specification;
        private String dosage;
        private String usageMethod;
        private String frequency;
        private Integer days;
        private Integer quantity;
        /** 单价（开单时取药品参考价） */
        private java.math.BigDecimal price;
        private String unit;
        private String remark;
    }
}
