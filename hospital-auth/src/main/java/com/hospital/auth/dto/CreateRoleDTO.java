package com.hospital.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建角色请求 DTO
 */
@Data
public class CreateRoleDTO {

    /** 角色编码 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过 50")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过 100")
    private String roleName;

    /** 角色描述 */
    @Size(max = 255, message = "角色描述长度不能超过 255")
    private String description;
}
