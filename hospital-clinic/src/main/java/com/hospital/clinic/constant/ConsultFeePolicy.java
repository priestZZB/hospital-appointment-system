package com.hospital.clinic.constant;

import java.math.BigDecimal;

/**
 * 门诊分层定价策略常量（迭代9 A5）。
 * <p>
 * 号别（{@code schedule.fee_type}）为 {@code EXPERT}（专家号）时，
 * 挂号费不再使用排班登记的 {@code register_fee}，而是按医生职称取档位价：
 * <ul>
 *   <li>主任医师（CHIEF）→ 50 元</li>
 *   <li>副主任医师（VICE_CHIEF / ASSOCIATE_CHIEF，两种取值均兼容）→ 40 元</li>
 *   <li>主治医师（ATTENDING）→ 30 元</li>
 *   <li>住院医师（RESIDENT）→ 20 元</li>
 * </ul>
 * 号别为 {@code NORMAL}（普通号）时沿用排班 {@code register_fee}，不经过本类。
 */
public final class ConsultFeePolicy {

    private ConsultFeePolicy() {
    }

    /** 号别：普通号 */
    public static final String FEE_TYPE_NORMAL = "NORMAL";
    /** 号别：专家号 */
    public static final String FEE_TYPE_EXPERT = "EXPERT";

    /** 主任医师档位价（元） */
    public static final BigDecimal CHIEF_FEE = new BigDecimal("50");
    /** 副主任医师档位价（元） */
    public static final BigDecimal ASSOCIATE_CHIEF_FEE = new BigDecimal("40");
    /** 主治医师档位价（元） */
    public static final BigDecimal ATTENDING_FEE = new BigDecimal("30");
    /** 住院医师档位价（元） */
    public static final BigDecimal RESIDENT_FEE = new BigDecimal("20");

    /**
     * 按号别与医生职称解析挂号费。
     *
     * @param feeType        号别：NORMAL-普通号 / EXPERT-专家号（null 视为普通号）
     * @param doctorTitle    医生职称：CHIEF / VICE_CHIEF / ASSOCIATE_CHIEF / ATTENDING / RESIDENT
     * @param fallbackFee    档位无法匹配或号别为普通号时使用的兜底费用（排班 register_fee）
     * @return 挂号费（专家号按职称档位价，普通号或无法匹配时返回 fallbackFee）
     */
    public static BigDecimal resolveFee(String feeType, String doctorTitle, BigDecimal fallbackFee) {
        // 普通号（或未指定号别）沿用排班登记价，保持既有行为
        if (feeType == null || feeType.isBlank() || FEE_TYPE_NORMAL.equalsIgnoreCase(feeType)) {
            return fallbackFee;
        }
        if (!FEE_TYPE_EXPERT.equalsIgnoreCase(feeType) || doctorTitle == null || doctorTitle.isBlank()) {
            return fallbackFee;
        }
        switch (doctorTitle) {
            case "CHIEF":
                return CHIEF_FEE;
            case "VICE_CHIEF":          // 实际存储取值（见 doctor.title 注释）
            case "ASSOCIATE_CHIEF":     // 任务命名兼容写法
                return ASSOCIATE_CHIEF_FEE;
            case "ATTENDING":
                return ATTENDING_FEE;
            case "RESIDENT":
                return RESIDENT_FEE;
            default:
                // 未知职称：兜底为排班登记价，避免挂号失败
                return fallbackFee;
        }
    }
}
