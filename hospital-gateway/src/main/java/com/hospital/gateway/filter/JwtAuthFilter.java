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

import org.springframework.util.AntPathMatcher;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证全局过滤器
 * <p>
 * 请求进入 Gateway 后：
 * 1. 白名单路径直接放行
 * 2. 从 Authorization Header 提取 Bearer Token
 * 3. 验签 + 过期校验
 * 4. 检查 Token 是否在 Redis 黑名单中
 * 5. 读取用户权限码（Redis perm:user:{userId}）并注入 Header 透传下游微服务
 *    （X-User-Id / X-User-Roles / X-User-Permissions）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements WebFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /** 权限缓存 key 前缀（与 common PermissionCacheService 保持一致） */
    private static final String PERMISSION_KEY_PREFIX = "perm:user:";

    /**
     * JWT 白名单路径（逗号分隔）。
     * 优先从 Nacos 配置中心读取，不存在时使用本地默认值。
     */
    @Value("${gateway.jwt.whitelist:/api/auth/register,/api/auth/login,/api/ws/**,/v3/api-docs/**,/swagger-ui.html,/swagger-ui/**,/webjars/**}")
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
                .onErrorResume(e -> {
                    log.warn("[JWT] Redis 不可用，黑名单校验跳过: {}", e.getMessage());
                    return Mono.just(false);
                })
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("[JWT] Token 在黑名单中, path={}", path);
                        return writeUnauthorized(exchange, ErrorCodeEnum.TOKEN_BLACKLISTED);
                    }

                    // ========== 5. 注入用户信息 Header ==========
                    Long userId;
                    try {
                        userId = jwtUtil.getUserId(token);
                    } catch (NumberFormatException e) {
                        log.warn("[JWT] Token 中 userId 非数字格式, path={}", path);
                        return writeUnauthorized(exchange, ErrorCodeEnum.TOKEN_INVALID);
                    }
                    if (userId == null) {
                        log.warn("[JWT] Token 中未包含用户ID, path={}", path);
                        return writeUnauthorized(exchange, ErrorCodeEnum.TOKEN_INVALID);
                    }
                    List<String> roles = jwtUtil.getRoles(token);
                    String rolesStr = (roles != null && !roles.isEmpty())
                            ? String.join(",", roles)
                            : "";

                    log.debug("[JWT] 认证通过: userId={}, roles={}, path={}", userId, rolesStr, path);

                    // 读取用户权限码（Redis Set），注入 X-User-Permissions Header
                    return redisTemplate.opsForSet().members(PERMISSION_KEY_PREFIX + userId)
                            .collectList()
                            .onErrorResume(e -> {
                                log.warn("[JWT] Redis 不可用，权限码读取跳过（fail-open）: {}", e.getMessage());
                                return Mono.just(List.of());
                            })
                            .flatMap(perms -> {
                                String permsStr = (perms != null && !perms.isEmpty())
                                        ? String.join(",", perms)
                                        : "";

                                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                        .header("X-User-Id", String.valueOf(userId))
                                        .header("X-User-Roles", rolesStr)
                                        .header("X-User-Permissions", permsStr)
                                        .build();

                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            });
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
        String[] patterns = whitelistStr.split(",");
        for (String pattern : patterns) {
            if (antPathMatcher.match(pattern.trim(), path)) {
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
        // 非 Bearer 格式直接视为无效
        return null;
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
