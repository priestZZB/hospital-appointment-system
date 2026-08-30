package com.hospital.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 接口权限校验拦截器（仅 Servlet MVC 应用激活，Gateway 不加载）。
 * <p>
 * 在 {@link AuthInterceptor} 之后执行（注册顺序保证）：
 * <ol>
 *   <li>解析 Controller 方法/类上的 {@link RequiresPermission} 注解；</li>
 *   <li>未标注注解 → 放行（仅需登录）；</li>
 *   <li>已标注 → 校验当前用户是否拥有任一权限码（{@link UserContext#hasAnyPermission}，
 *       超级管理员与通配码 {@code *:*:*} 直接放行）；</li>
 *   <li>不满足 → 返回 {@code 1003 权限不足}。</li>
 * </ol>
 * <p>
 * 内部接口（/api 下各服务内 /internal 路径）由 AuthInterceptor 排除且无权限码，本拦截器同样跳过。
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 1. 读取注解：方法级优先，其次类级
        RequiresPermission requires = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (requires == null) {
            requires = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (requires == null) {
            return true;
        }

        // 2. 校验权限（任一命中即放行）
        String[] permCodes = requires.value();
        if (UserContext.hasAnyPermission(permCodes)) {
            return true;
        }

        // 3. 权限不足
        log.warn("[权限拦截] 权限不足: uri={}, userId={}, required={}, owned={}",
                request.getRequestURI(),
                UserContext.getUserId(),
                Arrays.toString(permCodes),
                UserContext.getPermissions());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(ErrorCodeEnum.NO_PERMISSION);
        response.getWriter().write(MAPPER.writeValueAsString(result));
        return false;
    }
}