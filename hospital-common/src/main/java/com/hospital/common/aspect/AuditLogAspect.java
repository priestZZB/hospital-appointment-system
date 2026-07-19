package com.hospital.common.aspect;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 审计日志切面
 * <p>
 * 拦截标注了 @AuditLog 的方法，记录操作日志。
 * 当前迭代仅输出日志，后续迭代接入数据库 + 异步化。
 * <p>
 * 异步化注意事项（记录备查）：
 * <ol>
 *   <li>需在配置类添加 {@code @EnableAsync} 并配置线程池</li>
 *   <li>异步方法无法通过 {@code UserContext} ThreadLocal 获取用户信息，
 *       需在主线程提前捕获 userId 作为参数传入</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    /**
     * 环绕通知：记录操作耗时、操作用户、操作描述
     */
    @Around("@annotation(com.hospital.common.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        Throwable error = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            // 在主线程提前捕获 userId，避免后续异步化时 ThreadLocal 失效
            Long userId = UserContext.getUserId();
            recordLog(joinPoint, userId, elapsed, error == null);
        }

        return result;
    }

    /**
     * 写入审计日志（当前为日志输出，后续改为数据库插入）
     *
     * @param joinPoint 切点
     * @param userId    操作人 ID（由调用方传入，避免 ThreadLocal 跨线程问题）
     * @param elapsed   执行耗时（毫秒）
     * @param success   是否成功
     */
    void recordLog(ProceedingJoinPoint joinPoint, Long userId, long elapsed, boolean success) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditLog auditLog = method.getAnnotation(AuditLog.class);

            String operation = auditLog.value();
            if (operation.isBlank()) {
                operation = signature.getDeclaringTypeName() + "." + method.getName();
            }

            log.info("[审计日志] userId={}, operation={}, operationType={}, elapsed={}ms, success={}",
                    userId != null ? userId : "-",
                    operation,
                    auditLog.operationType(),
                    elapsed,
                    success);
        } catch (Exception e) {
            // 审计日志失败不影响主流程
            log.warn("[审计日志] 记录失败: {}", e.getMessage());
        }
    }
}
