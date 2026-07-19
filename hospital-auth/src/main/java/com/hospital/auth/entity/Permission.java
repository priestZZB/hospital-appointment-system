package com.hospital.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限表实体
 */
@Data
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 权限标识符 */
    private String permCode;

    /** 权限名称 */
    private String permName;

    /** 权限类型：MENU / BUTTON / API */
    private String permType;

    /** 父权限 ID，构建菜单树 */
    private Long parentId;

    /** 前端路由路径 */
    private String path;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
