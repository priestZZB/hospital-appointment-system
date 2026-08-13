package com.hospital.auth.service;

import com.hospital.auth.dto.AuditLogQueryDTO;
import com.hospital.auth.entity.AuditLog;
import com.hospital.auth.entity.User;
import com.hospital.auth.mapper.AuditLogMapper;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.common.audit.AuditLogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志服务：跨服务写入 + 管理端分页查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    /**
     * 写入审计日志（内部接口：各微服务 Feign 调用）
     *
     * @param entry 审计日志条目
     */
    public void record(AuditLogEntry entry) {
        if (entry == null || entry.getOperation() == null || entry.getOperation().isBlank()) {
            return;
        }
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(entry.getUserId());
            auditLog.setUsername(resolveUsername(entry));
            auditLog.setOperation(entry.getOperation());
            auditLog.setHttpMethod(entry.getHttpMethod());
            auditLog.setRequestUri(entry.getRequestUri());
            auditLog.setRequestIp(entry.getRequestIp());
            auditLog.setRequestParams(entry.getRequestParams());
            auditLog.setResponseResult(entry.getResponseResult());
            auditLog.setExecutionTime(entry.getExecutionTime());
            auditLog.setStatus(entry.getStatus() != null ? entry.getStatus() : 1);
            auditLog.setErrorMessage(entry.getErrorMessage());
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("[审计日志] 落库失败（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 管理端分页查询
     */
    public UserService.PageResult<AuditLog> pageQuery(AuditLogQueryDTO dto) {
        List<AuditLog> list = auditLogMapper.pageQuery(
                dto.getUserId(),
                dto.getOperationType(),
                dto.getStartTime(),
                dto.getEndTime(),
                dto.getOffset(),
                dto.getPageSize());
        long total = auditLogMapper.countPageQuery(
                dto.getUserId(),
                dto.getOperationType(),
                dto.getStartTime(),
                dto.getEndTime());
        return new UserService.PageResult<>(list, total, dto.getPageNo(), dto.getPageSize());
    }

    private String resolveUsername(AuditLogEntry entry) {
        if (entry.getUsername() != null && !entry.getUsername().isBlank()) {
            return entry.getUsername();
        }
        if (entry.getUserId() != null) {
            User user = userMapper.selectById(entry.getUserId());
            if (user != null) {
                return user.getRealName();
            }
        }
        return null;
    }
}
