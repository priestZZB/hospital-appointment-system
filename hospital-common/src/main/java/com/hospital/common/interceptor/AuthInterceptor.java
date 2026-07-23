package com.hospital.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证拦截器（仅 Servlet MVC 应用激活，Gateway 不加载）。
 * <p>
 * Gateway 已完成 Token 验签并将用户信息注入 Header，
 * 本拦截器从 Header 中提取用户信息并写入 UserContext。
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String userId = request.getHeader(HEADER_USER_ID);
        String roles = request.getHeader(HEADER_USER_ROLES);

        if (userId == null || userId.isBlank()) {
            log.warn("[认证拦截] 缺少用户信息, uri={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Result<Void> result = Result.fail(ErrorCodeEnum.NOT_LOGIN);
            response.getWriter().write(MAPPER.writeValueAsString(result));
            return false;
        }

        try {
            UserContext.setUserId(Long.valueOf(userId));
        } catch (NumberFormatException e) {
            log.warn("[认证拦截] 无效的用户ID格式, uri={}, userId={}", request.getRequestURI(), userId);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Result<Void> result = Result.fail(ErrorCodeEnum.NOT_LOGIN);
            response.getWriter().write(MAPPER.writeValueAsString(result));
            return false;
        }

        List<String> roleList;
        if (roles != null && !roles.isBlank()) {
            roleList = java.util.Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else {
            roleList = Collections.emptyList();
        }
        UserContext.setRoles(roleList);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
