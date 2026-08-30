package com.hospital.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限校验注解（迭代 5 权限底座）。
 * <p>
 * 标注在 Controller 方法（或类）上，声明访问该接口所需的权限码。
 * 由 {@code com.hospital.common.interceptor.PermissionInterceptor} 统一拦截校验：
 * <ul>
 *   <li>用户拥有任一声明的权限码（{@code value} 数组为「或」关系）即放行；</li>
 *   <li>超管（{@link com.hospital.common.constant.RoleConstant#SUPER_ADMIN}）默认放行；</li>
 *   <li>未标注本注解的接口不做权限校验（仅需登录）。</li>
 * </ul>
 * <p>
 * 权限码必须引用 {@code com.hospital.common.constant.PermissionConstant}，禁止硬编码字符串。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 所需权限码（任一命中即放行）。
     * <p>
     * 示例：{@code @RequiresPermission(PermissionConstant.AUTH_ROLE_CREATE)}
     */
    String[] value();
}