package com.hospital.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * <p>
 * 标注在 Controller 方法上，AOP 切面自动记录操作日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作描述，如：用户登录、挂号下单 */
    String value() default "";

    /** 操作类型：INSERT / UPDATE / DELETE / QUERY / LOGIN / LOGOUT / OTHER */
    String operationType() default "OTHER";
}
