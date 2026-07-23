package com.hospital.auth.controller;

import com.hospital.auth.dto.LoginDTO;
import com.hospital.auth.dto.RegisterDTO;
import com.hospital.auth.service.AuthService;
import com.hospital.auth.vo.LoginVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 患者注册
     */
    @AuditLog(value = "用户注册", operationType = "INSERT")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.ok();
    }

    /**
     * 账号登录
     */
    @AuditLog(value = "用户登录", operationType = "LOGIN")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = getClientIp(request);
        return Result.ok(authService.login(dto, ip));
    }

    /**
     * 登出
     */
    @AuditLog(value = "用户登出", operationType = "LOGOUT")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        authService.logout(token);
        return Result.ok();
    }

    /**
     * Token 刷新
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(HttpServletRequest request) {
        String token = extractToken(request);
        return Result.ok(authService.refresh(token));
    }

    // ==================== 私有方法 ====================

    /**
     * 从 Authorization Header 提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return header != null ? header.trim() : null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
