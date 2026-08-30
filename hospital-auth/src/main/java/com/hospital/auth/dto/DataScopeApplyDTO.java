package com.hospital.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交跨科室数据范围申请 DTO
 */
@Data
public class DataScopeApplyDTO {

    /** 目标科室 ID（clinic.department.id） */
    @NotNull(message = "目标科室不能为空")
    private Long targetDepartmentId;

    /** 目标科室名称（冗余） */
    @Size(max = 100, message = "科室名称不能超过100个字符")
    private String targetDepartmentName;

    /** 申请理由 */
    @NotBlank(message = "申请理由不能为空")
    @Size(max = 500, message = "申请理由不能超过500个字符")
    private String reason;
}