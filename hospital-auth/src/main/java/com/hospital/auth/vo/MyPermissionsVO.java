package com.hospital.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 我的权限响应 VO（GET /api/auth/my-permissions）
 * <p>
 * 权限码按 {@code api:*} / {@code menu:*} / {@code btn:*} 三类下发，
 * 前端用于路由守卫 / 菜单渲染 / 按钮控制。
 * 超管（ROLE_SUPER_ADMIN）返回通配码 {@code *:*:*}（拥有全部权限）。
 */
@Data
@Builder
public class MyPermissionsVO {

    /** 用户 ID */
    private Long userId;

    /** 是否超级管理员（拥有全部权限） */
    private Boolean superAdmin;

    /** 全部权限码（去重） */
    private List<String> permissions;

    /** API 接口权限码（导航/守卫用，= permissions 中 api: 前缀） */
    private List<String> apiPermissions;

    /** 菜单权限码（menu: 前缀） */
    private List<String> menuPermissions;

    /** 按钮权限码（btn: 前缀） */
    private List<String> btnPermissions;
}