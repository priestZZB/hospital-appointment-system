package com.hospital.common.audit;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志传输对象（跨服务采集，Feign 写入 auth-service audit_log 表）
 */
@Data
public class AuditLogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作人 ID */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 操作类型描述 */
    private String operation;

    /** HTTP 方法 */
    private String httpMethod;

    /** 请求 URI */
    private String requestUri;

    /** 请求来源 IP */
    private String requestIp;

    /** 请求参数（截断至 2000 字符） */
    private String requestParams;

    /** 响应结果（截断至 2000 字符） */
    private String responseResult;

    /** 执行耗时（毫秒） */
    private Long executionTime;

    /** 状态：1-成功 0-异常 */
    private Integer status;

    /** 异常信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
