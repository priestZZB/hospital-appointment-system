package com.hospital.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 岗位创建/编辑 DTO
 */
@Data
public class PositionSaveDTO {

    /** 岗位编码（唯一） */
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 50, message = "岗位编码不能超过50个字符")
    private String positionCode;

    /** 岗位名称 */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称不能超过100个字符")
    private String positionName;

    /** 所属部门 ID（clinic.department.id） */
    @NotNull(message = "所属部门不能为空")
    private Long departmentId;

    /** 所属部门名称（冗余展示） */
    @Size(max = 100, message = "部门名称不能超过100个字符")
    private String departmentName;

    /** 职务 */
    @NotBlank(message = "职务不能为空")
    @Size(max = 50, message = "职务不能超过50个字符")
    private String title;

    /** 岗位描述 */
    @Size(max = 255, message = "岗位描述不能超过255个字符")
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 排序号 */
    private Integer sortOrder;
}