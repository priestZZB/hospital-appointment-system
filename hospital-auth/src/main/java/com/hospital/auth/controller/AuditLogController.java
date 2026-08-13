package com.hospital.auth.controller;

import com.hospital.auth.dto.AuditLogQueryDTO;
import com.hospital.auth.entity.AuditLog;
import com.hospital.auth.service.AuditLogService;
import com.hospital.auth.service.UserService;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询接口（仅管理员）
 */
@RestController
@RequestMapping("/api/auth/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志（操作人/操作类型/时间范围筛选）
     */
    @GetMapping
    public Result<UserService.PageResult<AuditLog>> pageQuery(@ModelAttribute AuditLogQueryDTO dto) {
        checkAdmin();
        return Result.ok(auditLogService.pageQuery(dto));
    }

    private void checkAdmin() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }
}
