package com.hospital.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.audit.AuditLogEntry;
import com.hospital.common.feign.AuditFeignClient;
import com.hospital.common.interceptor.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 审计日志切面
 * <p>
 * 拦截标注了 @AuditLog 的方法，记录操作日志：
 * <ol>
 *   <li>输出本地日志（保留原行为）；</li>
 *   <li>通过 {@link AuditFeignClient} 异步写入 auth-service 的 audit_log 表（失败不影响主流程）。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_RESULT_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 500;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    /** 独立守护线程池，审计写入失败不影响业务主流程 */
    private static final ExecutorService AUDIT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "audit-log-writer");
        t.setDaemon(true);
        return t;
    });

    private final ObjectProvider<AuditFeignClient> auditFeignClientProvider;

    /**
     * 环绕通知：记录操作耗时、操作用户、操作描述，并异步落库
     */
    @Around("@annotation(com.hospital.common.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            // 在主线程提前捕获 userId，避免异步线程 ThreadLocal 失效
            Long userId = UserContext.getUserId();
            recordLog(joinPoint, userId, elapsed, error == null, error, result);
        }

        return result;
    }

    /**
     * 记录审计日志：本地日志 + 异步 Feign 落库
     *
     * @param joinPoint 切点
     * @param userId    操作人 ID
     * @param elapsed   执行耗时（毫秒）
     * @param success   是否成功
     * @param error     异常信息
     * @param result    响应结果
     */
    void recordLog(ProceedingJoinPoint joinPoint, Long userId, long elapsed, boolean success,
                   Throwable error, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditLog auditLog = method.getAnnotation(AuditLog.class);

            String operation = auditLog.value();
            if (operation == null || operation.isBlank()) {
                operation = signature.getDeclaringTypeName() + "." + method.getName();
            }

            log.info("[审计日志] userId={}, operation={}, operationType={}, elapsed={}ms, success={}",
                    userId != null ? userId : "-",
                    operation,
                    auditLog.operationType(),
                    elapsed,
                    success);

            AuditLogEntry entry = buildEntry(joinPoint, userId, operation, elapsed, success, error, result);
            AUDIT_EXECUTOR.submit(() -> {
                try {
                    AuditFeignClient client = auditFeignClientProvider.getIfAvailable();
                    if (client != null) {
                        client.record(entry);
                    }
                } catch (Exception e) {
                    log.warn("[审计日志] 异步落库失败（忽略）: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            // 审计日志失败不影响主流程
            log.warn("[审计日志] 记录失败: {}", e.getMessage());
        }
    }

    private AuditLogEntry buildEntry(ProceedingJoinPoint joinPoint, Long userId, String operation,
                                     long elapsed, boolean success, Throwable error, Object result) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setUserId(userId);
        entry.setOperation(operation);
        entry.setExecutionTime(elapsed);
        entry.setStatus(success ? 1 : 0);
        entry.setErrorMessage(error != null ? truncate(String.valueOf(error.getMessage()), MAX_ERROR_LENGTH) : null);

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setHttpMethod(request.getMethod());
            entry.setRequestUri(request.getRequestURI());
            entry.setRequestIp(resolveIp(request));
        }

        entry.setRequestParams(truncate(toJson(joinPoint.getArgs()), MAX_PARAM_LENGTH));
        entry.setResponseResult(success ? truncate(toJson(result), MAX_RESULT_LENGTH) : null);
        return entry;
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // 非 Web 请求上下文时忽略
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            // 参数不可序列化（如 MultipartFile、HttpServletRequest）时降级为 toString
            return String.valueOf(value);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
