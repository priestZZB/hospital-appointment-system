package com.hospital.common.feign;

import com.hospital.common.audit.AuditLogEntry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 审计日志 Feign 客户端
 * <p>
 * 供各微服务（auth/patient/clinic/medsupply/payment/ai）将 {@code @AuditLog}
 * 操作记录异步写入 auth-service 的 audit_log 表。
 */
@FeignClient(name = "auth-service", path = "/api/auth/internal")
public interface AuditFeignClient {

    /**
     * 写入审计日志（内部接口，Feign 直连 auth-service，不经过网关）
     *
     * @param entry 审计日志条目
     * @return 写入结果
     */
    @PostMapping("/audit-log")
    Map<String, Object> record(@RequestBody AuditLogEntry entry);
}
