package com.hospital.common.config;

import com.hospital.common.interceptor.AuthInterceptor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/patient/internal/**"   // Feign 内部调用不需要 Header
                );
    }
}
