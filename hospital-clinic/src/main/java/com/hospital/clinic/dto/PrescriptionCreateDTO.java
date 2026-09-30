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

    /** 处方类型：WESTERN-西药/中成药处方笺（默认） / HERBAL-中药饮片处方笺（V9，可选） */
    private String prescriptionType;

    /** 中药剂数（HERBAL 处方必填，如 7 剂）（V9，可选） */
    private Integer herbalDoses;

    /** 煎服法（如：每日一剂，水煎400ml，分早晚两次温服）（V9，可选） */
    private String herbalUsage;

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
        /** 中药煎法：先煎/后下/包煎/烊化等（HERBAL 明细使用，V9，可选） */
        private String decoctionMethod;
        /** 中药脚注：特殊处理说明（HERBAL 明细使用，V9，可选） */
        private String footnote;
    }
}
