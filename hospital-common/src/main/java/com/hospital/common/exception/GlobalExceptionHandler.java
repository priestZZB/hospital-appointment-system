package com.hospital.common.exception;

import com.hospital.common.result.Result;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（仅 Servlet MVC 应用激活，Gateway/WebFlux 跳过）。
 * <p>
 * 统一拦截各类异常，返回 Result 格式的响应。
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] uri={}, code={}, message={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（Jakarta Validation）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] uri={}, detail={}", request.getRequestURI(), detail);
        return Result.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), detail);
    }

    /**
     * Feign 远程调用异常
     */
    @ExceptionHandler(FeignException.class)
    public Result<Void> handleFeignException(FeignException e, HttpServletRequest request) {
        log.error("[远程调用失败] uri={}, status={}, message={}", request.getRequestURI(), e.status(), e.getMessage());
        return Result.fail(ErrorCodeEnum.REMOTE_SERVICE_ERROR);
    }

    /**
     * 其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknownException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] uri={}", request.getRequestURI(), e);
        return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
    }
}
