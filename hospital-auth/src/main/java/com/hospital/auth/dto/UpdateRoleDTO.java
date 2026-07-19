package com.hospital.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新角色请求 DTO
 */
@Data
public class UpdateRoleDTO {

    /** 角色名称 */
    @Size(max = 100, message = "角色名称长度不能超过 100")
    private String roleName;

    /** 角色描述 */
    @Size(max = 255, message = "角色描述长度不能超过 255")
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}
