package com.hospital.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 统一使用 ErrorCodeEnum 或自定义 code/message 抛出，
 * 由 GlobalExceptionHandler 统一拦截处理。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final Integer code;

    /**
     * 使用错误码枚举构造
     */
    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用错误码枚举 + 自定义消息构造
     */
    public BusinessException(ErrorCodeEnum errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 使用自定义 code + message 构造
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
