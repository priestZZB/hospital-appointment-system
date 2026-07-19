package com.hospital.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 为用户分配角色请求 DTO
 */
@Data
public class AssignRoleDTO {

    /** 用户 ID */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 角色 ID 列表 */
    @NotNull(message = "角色 ID 列表不能为空")
    private List<Long> roleIds;
}
