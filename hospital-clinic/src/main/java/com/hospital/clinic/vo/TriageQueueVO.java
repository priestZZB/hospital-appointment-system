package com.hospital.clinic.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分诊台队列患者 VO（迭代9 A1）
 * <p>
 * 分诊台视图：WAITING 患者按分诊 score 升序排列
 * （score = priorityRank × 1e13 + 签到时间戳毫秒，回诊标记减 0.5 档，
 * 与 Redis 叫号队列 ZPOPMIN 的取号顺序一致）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageQueueVO {

    /** 签到记录 ID */
    private Long checkinId;

    /** 预约 ID */
    private Long appointmentId;

    /** 患者 ID */
    private Long patientId;

    /** 患者姓名（Feign 补充） */
    private String patientName;

    /** 科室 ID */
    private Long departmentId;

    /** 医生 ID */
    private Long doctorId;

    /** 排队状态（恒为 WAITING） */
    private String queueStatus;

    /** 分诊优先级：0-急诊 / 1-优先 / 2-普通 */
    private Integer priority;

    /** 回诊标记：0-初诊排队 / 1-检查检验完成回诊 */
    private Integer returnFlag;

    /** 签到时间（原队列 score 的时间基准） */
    private LocalDateTime checkinTime;

    /** 分诊操作时间 */
    private LocalDateTime triageTime;

    /** 排队 score（与 Redis ZSet 一致，仅用于前端展示/调试） */
    private Double queueScore;
}
