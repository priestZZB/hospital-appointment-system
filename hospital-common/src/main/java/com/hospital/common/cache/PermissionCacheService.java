package com.hospital.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 用户权限码 Redis 缓存（迭代 5 权限底座）。
 * <p>
 * 链路：
 * <ol>
 *   <li>auth-service 登录/刷新时调用 {@link #cachePermissions} 将用户权限码写入 Redis
 *       （Set 结构，key = {@code perm:user:{userId}}，TTL = JWT 有效期）；</li>
 *   <li>Gateway {@code JwtAuthFilter} 每请求读取 {@link #getPermissions} 并注入
 *       {@code X-User-Permissions} Header 透传下游；</li>
 *   <li>各服务 {@code AuthInterceptor} 解析 Header 写入 {@code UserContext}，
 *       {@code PermissionInterceptor} 按 {@code @RequiresPermission} 注解校验。</li>
 * </ol>
 * <p>
 * Redis 不可用时 fail-open（读返回空集合、写仅记录日志），保证核心业务不受缓存故障影响
 * （与 Gateway 黑名单校验的 fail-open 策略一致）。权限码变更后由 auth-service 主动失效。
 * <p>
 * 仅 Servlet MVC 应用加载（auth/patient/clinic/medsupply/payment/ai）；
 * Gateway（WebFlux）自行用 ReactiveStringRedisTemplate 读取，不加载本类。
 * <p>
 * 通过 {@link ObjectProvider} 懒获取 StringRedisTemplate：未引入 Redis 依赖的服务
 * （如 patient-service）不会因缺少 Bean 启动失败，相关方法自动降级为空操作。
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionCacheService {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /** 权限缓存 key 前缀：perm:user:{userId} */
    public static final String PERMISSION_KEY_PREFIX = "perm:user:";
    /** 通配权限码（超管） */
    public static final String ALL_PERMISSIONS = "*:*:*";

    public PermissionCacheService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 将用户权限码写入 Redis（覆盖式，Set 结构）。
     *
     * @param userId      用户 ID
     * @param permissions 权限码列表（null 视为空）
     * @param ttl         缓存有效期（一般等于 JWT 有效期）
     */
    public void cachePermissions(Long userId, List<String> permissions, Duration ttl) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (userId == null || redis == null) {
            return;
        }
        try {
            String key = PERMISSION_KEY_PREFIX + userId;
            redis.delete(key);
            if (permissions != null && !permissions.isEmpty()) {
                redis.opsForSet().add(key, permissions.toArray(new String[0]));
                if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
                    redis.expire(key, ttl);
                }
            }
            log.debug("[权限缓存] 已写入: userId={}, count={}", userId,
                    permissions == null ? 0 : permissions.size());
        } catch (Exception e) {
            log.warn("[权限缓存] 写入失败（fail-open）: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 读取用户权限码。
     *
     * @param userId 用户 ID
     * @return 权限码集合（Redis 不可用/无缓存时返回空集合，不抛异常）
     */
    public List<String> getPermissions(Long userId) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (userId == null || redis == null) {
            return Collections.emptyList();
        }
        try {
            String key = PERMISSION_KEY_PREFIX + userId;
            Set<String> perms = redis.opsForSet().members(key);
            if (perms == null || perms.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(perms);
        } catch (Exception e) {
            log.warn("[权限缓存] 读取失败（fail-open）: userId={}, error={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 删除用户权限缓存（角色/权限变更后调用，强制下次登录重新加载）。
     */
    public void removePermissions(Long userId) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (userId == null || redis == null) {
            return;
        }
        try {
            redis.delete(PERMISSION_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[权限缓存] 删除失败（fail-open）: userId={}, error={}", userId, e.getMessage());
        }
    }
}