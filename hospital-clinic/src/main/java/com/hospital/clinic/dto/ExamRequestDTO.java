package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 检查申请请求 DTO
 */
@Data
public class ExamRequestDTO {

    /** 病历ID */
    @NotNull(message = "病历ID不能为空")
    private Long medicalRecordId;

    /** 检查项目ID */
    @NotNull(message = "检查项目ID不能为空")
    private Long examItemId;

    /** 检查项目名称 */
    @NotBlank(message = "检查项目名称不能为空")
    private String examItemName;

    /** 项目类型 */
    @NotBlank(message = "项目类型不能为空")
    private String itemType;

    /** 申请备注 */
    private String applyRemark;
}
