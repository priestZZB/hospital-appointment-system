package com.hospital.common.interceptor;

import java.util.Collections;
import java.util.List;

/**
 * 用户上下文（ThreadLocal）
 * <p>
 * 网关 JwtAuthFilter 验签后通过 Header 透传用户信息，
 * AuthInterceptor 解析 Header 后将信息写入本类，
 * Service 层通过本类获取当前登录用户信息。
 * <p>
 * 使用完毕后必须调用 {@link #clear()} 清理，防止内存泄漏。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = ThreadLocal.withInitial(Collections::emptyList);

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setRoles(List<String> roles) {
        ROLES.set(roles);
    }

    public static List<String> getRoles() {
        return ROLES.get();
    }

    /**
     * 判断当前用户是否拥有指定角色
     */
    public static boolean hasRole(String roleCode) {
        List<String> list = ROLES.get();
        return list != null && list.contains(roleCode);
    }

    /**
     * 清理 ThreadLocal（必须搭配拦截器 afterCompletion 调用）
     */
    public static void clear() {
        USER_ID.remove();
        ROLES.remove();
    }
}
