package com.hospital.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色信息 VO
 */
@Data
@Builder
public class RoleVO {

    /** 角色 ID */
    private Long id;

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
