package com.hospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 过敏史新增 DTO
 */
@Data
public class AllergyDTO {

    @NotBlank(message = "过敏原不能为空")
    @Size(max = 100, message = "过敏原名称长度不能超过 100")
    private String allergen;

    private String reactionType;
    private String severity;
}
