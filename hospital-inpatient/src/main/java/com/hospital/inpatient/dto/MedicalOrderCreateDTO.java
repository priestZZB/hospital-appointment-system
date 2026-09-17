package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 住院医嘱开立 DTO */
@Data
public class MedicalOrderCreateDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    /** LONG_TERM / TEMPORARY */
    @NotBlank(message = "医嘱类型不能为空")
    private String orderType;
    /** DRUG / EXAM / LAB / INFUSION / NURSING / DIET / OTHER */
    @NotBlank(message = "医嘱类别不能为空")
    private String category;
    @NotBlank(message = "医嘱内容不能为空")
    private String content;
    private String frequency;
}
