package com.hospital.auth.controller;

import com.hospital.auth.service.AuditLogService;
import com.hospital.common.audit.AuditLogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审计日志内部写入接口（供各微服务 Feign 直连调用，不经过网关）
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
public class AuditLogInternalController {

    private final AuditLogService auditLogService;

    @PostMapping("/audit-log")
    public Map<String, Object> record(@RequestBody AuditLogEntry entry) {
        auditLogService.record(entry);
        return Map.of("success", true);
    }
}
