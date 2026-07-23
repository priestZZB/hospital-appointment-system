package com.hospital.common.exception;

import lombok.Getter;

/**
 * 错误码枚举
 * <p>
 * 按业务域分段：
 * 1000-1999 通用错误
 * 2000-2999 预约挂号
 * 3000-3999 支付结算
 * 4000-4999 门诊诊疗
 * 5000-5999 医辅物资
 * 6000-6999 AI 服务
 * 9000-9999 系统错误
 */
@Getter
public enum ErrorCodeEnum {

    // ==================== 成功 ====================
    SUCCESS(0, "操作成功"),

    // ==================== 通用错误 1000-1999 ====================
    PARAM_ERROR(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必要参数"),
    NO_PERMISSION(1003, "权限不足"),
    NOT_LOGIN(1004, "未登录或令牌已过期"),
    TOKEN_EXPIRED(1005, "令牌已过期"),
    TOKEN_INVALID(1006, "令牌无效"),
    TOKEN_BLACKLISTED(1007, "令牌已失效"), // 用于网关 JwtAuthFilter 检查 Redis 黑名单后返回
    RESOURCE_NOT_FOUND(1008, "资源不存在"),
    DUPLICATE_OPERATION(1009, "请勿重复操作"),
    RATE_LIMIT_EXCEEDED(1010, "请求过于频繁，请稍后重试"),
    USER_DISABLED(1011, "账号已被禁用"),
    USER_NOT_FOUND(1012, "用户不存在"),
    PASSWORD_ERROR(1013, "密码错误"),
    PHONE_ALREADY_REGISTERED(1014, "该手机号已注册"),

    // ==================== 预约挂号 2000-2999 ====================
    SLOT_NOT_ENOUGH(2001, "该号源已被抢完"),
    SLOT_NOT_AVAILABLE(2002, "号源不可用"),
    DUPLICATE_APPOINTMENT(2003, "该时段已有预约，请勿重复挂号"),
    APPOINTMENT_NOT_FOUND(2004, "预约记录不存在"),
    APPOINTMENT_CANNOT_CANCEL(2005, "就诊时段已开始，不可取消"),
    SCHEDULE_NOT_FOUND(2006, "排班不存在"),
    NOT_CHECKIN_TIME(2007, "未到签到时间"),
    CHECKIN_EXPIRED(2008, "签到时间已过"),
    PATIENT_NOT_VERIFIED(2009, "请先完成实名认证"),
    SLOT_CANCELLED(2010, "该号源所属排班已取消"),
    QUEUE_EMPTY(2011, "当前队列无等待患者"),

    // ==================== 支付结算 3000-3999 ====================
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_TIMEOUT(3002, "订单已超时"),
    ORDER_ALREADY_PAID(3003, "订单已支付，请勿重复支付"),
    ORDER_ALREADY_CANCELLED(3004, "订单已取消"),
    ORDER_EXPIRED(3005, "订单已过期"),
    REFUND_FAILED(3006, "退款失败"),
    PAYMENT_FAILED(3007, "支付失败"),

    // ==================== 门诊诊疗 4000-4999 ====================
    PATIENT_NOT_CHECKED_IN(4001, "患者未签到，无法接诊"),
    ALREADY_CHECKED_IN(4002, "请勿重复签到"),
    RECORD_NOT_FOUND(4003, "病历不存在"),
    RECORD_ALREADY_SUBMITTED(4004, "病历已提交，不可修改"),
    PRESCRIPTION_NOT_FOUND(4005, "处方不存在"),
    PRESCRIPTION_ALREADY_REVIEWED(4006, "处方已审核"),
    CONSULTATION_IN_PROGRESS(4007, "已有进行中的接诊"),
    STOP_NOT_IN_48H(4008, "仅可申请未来48小时内的停诊"),
    STOP_CONFLICT(4009, "该时段存在已签到或就诊中记录，无法停诊"),
    STOP_ALREADY_PROCESSED(4010, "该停诊申请已处理"),
    DOCTOR_NOT_FOUND(4011, "医生不存在"),
    DEPARTMENT_NOT_FOUND(4012, "科室不存在"),

    // ==================== 医辅物资 5000-5999 ====================
    DRUG_NOT_FOUND(5001, "药品不存在"),
    DRUG_STOCK_NOT_ENOUGH(5002, "药品库存不足"),
    DRUG_DISPENSED(5003, "药品已发放"),
    PRESCRIPTION_NOT_REVIEWED(5004, "处方尚未审核，不可发药"),
    EXAM_ITEM_NOT_FOUND(5005, "检查项目不存在"),
    EXAM_APPLICATION_NOT_FOUND(5006, "检查申请不存在"),
    EXAM_REPORT_EXISTS(5007, "检查报告已录入"),

    // ==================== AI 服务 6000-6999 ====================
    AI_SERVICE_DEGRADED(6001, "AI服务暂不可用，已切换至关键词匹配模式"),
    AI_SERVICE_TIMEOUT(6002, "AI服务响应超时"),
    SYMPTOM_TOO_SHORT(6003, "症状描述过短，请至少输入2个字"),
    SYMPTOM_TOO_LONG(6004, "症状描述过长，请精简至200字以内"),

    // ==================== 系统错误 9000-9999 ====================
    SERVICE_UNAVAILABLE(9997, "服务暂不可用"),
    REMOTE_SERVICE_ERROR(9998, "远程服务调用失败"),
    SYSTEM_ERROR(9999, "系统繁忙，请稍后重试");

    /** 错误码 */
    private final Integer code;

    /** 错误信息 */
    private final String message;

    ErrorCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
