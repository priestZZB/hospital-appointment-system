package com.hospital.common.result;

import com.hospital.common.exception.ErrorCodeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 响应数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：0-成功，其他-失败 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 时间戳 */
    private Long timestamp;

    private Result() {
    }

    /**
     * 成功（无数据）
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 成功（携带数据）
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCodeEnum.SUCCESS.getCode();
        r.message = ErrorCodeEnum.SUCCESS.getMessage();
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    /**
     * 失败（自定义 code + message）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    /**
     * 失败（使用错误码枚举）
     */
    public static <T> Result<T> fail(ErrorCodeEnum errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }
}
