package com.hospital.common.exception;

import com.hospital.common.result.Result;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
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
                .map(f -> f.getField() + ":" + Objects.toString(f.getDefaultMessage(), ""))
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] uri={}, detail={}", request.getRequestURI(), detail);
        return Result.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), detail);
    }

    /**
     * 请求体格式错误（如 JSON 解析失败）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[请求体格式错误] uri={}, message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ErrorCodeEnum.PARAM_ERROR);
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[缺少请求参数] uri={}, param={}", request.getRequestURI(), e.getParameterName());
        return Result.fail(ErrorCodeEnum.PARAM_MISSING);
    }

    /**
     * 参数绑定异常（表单提交）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String detail = e.getFieldErrors().stream()
                .map(f -> f.getField() + ":" + Objects.toString(f.getDefaultMessage(), ""))
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] uri={}, detail={}", request.getRequestURI(), detail);
        return Result.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), detail);
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[请求方法不支持] uri={}, method={}", request.getRequestURI(), e.getMethod());
        return Result.fail(405, "请求方法不支持");
    }

    /**
     * 参数违规异常（如 @RequestParam 校验失败）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        log.warn("[参数违规] uri={}, message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ErrorCodeEnum.PARAM_ERROR);
    }

    /**
     * Feign 远程调用异常 — 4xx 客户端错误
     */
    @ExceptionHandler(FeignException.FeignClientException.class)
    public Result<Void> handleFeignClientException(FeignException.FeignClientException e, HttpServletRequest request) {
        log.warn("[远程调用失败-客户端错误] uri={}, status={}, message={}", request.getRequestURI(), e.status(), e.getMessage());
        return Result.fail(ErrorCodeEnum.REMOTE_SERVICE_ERROR);
    }

    /**
     * Feign 远程调用异常 — 5xx 服务端错误
     */
    @ExceptionHandler(FeignException.FeignServerException.class)
    public Result<Void> handleFeignServerException(FeignException.FeignServerException e, HttpServletRequest request) {
        log.error("[远程调用失败-服务端错误] uri={}, status={}, message={}", request.getRequestURI(), e.status(), e.getMessage());
        return Result.fail(ErrorCodeEnum.REMOTE_SERVICE_ERROR);
    }

    /**
     * Feign 远程调用异常 — 其他（无状态或网络异常）
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
