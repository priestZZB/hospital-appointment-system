package com.hospital.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类
 * <p>
 * HS256 算法签发/验签/解析 Token。
 * secret 和 expiration 可通过 Nacos 配置中心动态下发。
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;

    private final long expiration;

    /**
     * 通过构造注入配置，支持 Nacos 动态刷新
     *
     * @param secret     JWT 签名密钥（需 ≥256 bits）
     * @param expiration Token 有效期（秒），默认 7200（2小时）
     */
    public JwtUtil(@Value("${jwt.secret:hospital-appointment-system-jwt-secret-key-2026}") String secret,
                   @Value("${jwt.expiration:7200}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 签发 JWT Token
     *
     * @param userId 用户 ID
     * @param roles  角色列表
     * @return JWT 字符串
     */
    public String generate(Long userId, List<String> roles) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles != null ? roles : Collections.emptyList())
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     *
     * @param token JWT 字符串
     * @return Claims
     * @throws JwtException 验签失败或 Token 无效时抛出
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    /**
     * 从 Token 中提取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parse(token).get("roles", List.class);
    }

    /**
     * 判断 Token 是否已过期
     */
    public boolean isExpired(String token) {
        try {
            parse(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            // Token 格式不正确等, 不算过期而算无效
            return false;
        }
    }

    /**
     * 判断 Token 是否合法（验签 + 未过期）
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException e) {
            log.debug("Token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取 Token 剩余有效秒数
     */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parse(token);
            Date expiration = claims.getExpiration();
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (JwtException e) {
            return 0;
        }
    }
}
