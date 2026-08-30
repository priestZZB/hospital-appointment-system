package com.hospital.common.config;

import com.hospital.common.interceptor.AuthInterceptor;
import com.hospital.common.interceptor.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置（仅在 Servlet 容器下生效，WebFlux/Gateway 不加载）
 * <p>
 * 注册 AuthInterceptor 用于解析 Gateway 透传的 X-User-Id / X-User-Roles Header。
 * 白名单路径（login/register 等无需认证的公开接口）不拦截。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/internal/**",   // Feign 内部调用（审计日志写入）不需要 Header
                        "/api/patient/internal/**",   // Feign 内部调用不需要 Header
                        "/api/payment/internal/**",   // Feign 内部调用不需要 Header
                        "/api/clinic/internal/**",    // Feign 内部调用不需要 Header
                        "/api/medsupply/internal/**"  // Feign/RestTemplate 内部调用不需要 Header（检查申请等）
                );
        // 权限校验在认证之后执行（通过注册顺序保证：后注册的先执行，此处应让认证先跑）
        // 注意：Spring 拦截器按注册顺序 preHandle 正序执行，authInterceptor 在前、permissionInterceptor 在后
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/internal/**",
                        "/api/patient/internal/**",
                        "/api/payment/internal/**",
                        "/api/clinic/internal/**",
                        "/api/medsupply/internal/**"
                );
    }
}
