package com.hospital.common.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 药事安全审查 Feign 请求 DTO（供 clinic-service 开方时调用 medsupply-service）
 * <p>
 * 同时适配两个内部接口：
 * <ul>
 *   <li>CDSS 处方安全审查 {@code POST /api/medsupply/internal/pharmacy/cdss/check}（使用 patientId + items 全量字段）</li>
 *   <li>抗菌药物授权校验 {@code POST /api/medsupply/internal/pharmacy/antibiotic/check}（使用 doctorId + items.drugId）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyCheckDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 患者 ID（CDSS 审查用，如妊娠禁忌） */
    private Long patientId;

    /** 医生 ID（抗菌药物处方授权校验用） */
    private Long doctorId;

    /** 处方明细药品列表 */
    private List<Item> items;

    /**
     * 待审药品项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 药品 ID */
        private Long drugId;

        /** 药品名称 */
        private String drugName;

        /** 单次用量 */
        private String dosage;

        /** 数量 */
        private Integer quantity;

        /** 用药天数 */
        private Integer days;
    }
}
