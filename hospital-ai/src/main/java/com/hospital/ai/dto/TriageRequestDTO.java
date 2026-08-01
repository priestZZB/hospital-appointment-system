package com.hospital.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 分诊请求 DTO
 */
@Data
public class TriageRequestDTO {

    /** 症状描述 */
    @NotBlank(message = "症状描述不能为空")
    @Size(min = 2, max = 500, message = "症状描述需在2~500字之间")
    private String symptom;
}
