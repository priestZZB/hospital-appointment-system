package com.hospital.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证全局过滤器
 * <p>
 * 请求进入 Gateway 后：
 * 1. 白名单路径直接放行
 * 2. 从 Authorization Header 提取 Bearer Token
 * 3. 验签 + 过期校验
 * 4. 检查 Token 是否在 Redis 黑名单中
 * 5. 解析用户信息并注入 X-User-Id / X-User-Roles Header 透传给下游微服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements WebFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * JWT 白名单路径（逗号分隔）。
     * 优先从 Nacos 配置中心读取，不存在时使用本地默认值。
     */
    @Value("${gateway.jwt.whitelist:/api/auth/register,/api/auth/login,/api/ws/**}")
    private String whitelistStr;

    /**
     * 过滤器优先级：值越小越先执行，放在 sentinel 之后
     */
    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // ========== 1. 白名单放行 ==========
        if (isWhitelisted(path)) {
            log.debug("[JWT] 白名单路径放行: {}", path);
            return chain.filter(exchange);
        }

        // ========== 2. 提取 Token ==========
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            log.warn("[JWT] 缺少 Token, path={}", path);
            return writeUnauthorized(exchange, ErrorCodeEnum.NOT_LOGIN);
        }

        // ========== 3. 验签 + 过期校验 ==========
        if (!jwtUtil.isValid(token)) {
            log.warn("[JWT] Token 无效或已过期, path={}", path);
            return writeUnauthorized(exchange, ErrorCodeEnum.TOKEN_INVALID);
        }

        // ========== 4. Redis 黑名单校验（每个 token 独立 key） ==========
        return redisTemplate.hasKey("token:blacklist:" + token)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("[JWT] Token 在黑名单中, path={}", path);
                        return writeUnauthorized(exchange, ErrorCodeEnum.TOKEN_BLACKLISTED);
                    }

                    // ========== 5. 注入用户信息 Header ==========
                    Long userId = jwtUtil.getUserId(token);
                    List<String> roles = jwtUtil.getRoles(token);
                    String rolesStr = (roles != null && !roles.isEmpty())
                            ? String.join(",", roles)
                            : "";

                    log.debug("[JWT] 认证通过: userId={}, roles={}, path={}", userId, rolesStr, path);

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", String.valueOf(userId))
                            .header("X-User-Roles", rolesStr)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    // ==================== 私有方法 ====================

    /**
     * 判断路径是否在白名单中（支持 /api/ws/** 通配符）
     */
    private boolean isWhitelisted(String path) {
        if (whitelistStr == null || whitelistStr.isBlank()) {
            return false;
        }
        List<String> patterns = Arrays.asList(whitelistStr.split(","));
        for (String pattern : patterns) {
            String trimmed = pattern.trim();
            if (trimmed.endsWith("/**")) {
                String prefix = trimmed.substring(0, trimmed.length() - 3);
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else if (path.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 Authorization Header 中提取 Bearer Token
     */
    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        // 兼容直接传 Token 的情况
        return authHeader.trim();
    }

    /**
     * 返回 401 未授权 JSON 响应
     */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, ErrorCodeEnum errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> result = Result.fail(errorCode);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":9999,\"message\":\"系统异常\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
