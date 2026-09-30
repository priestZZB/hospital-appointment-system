package com.hospital.clinic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分诊设置优先级请求 DTO（迭代9 A1）
 * <p>
 * 分诊台护士对患者签到记录设置优先级与回诊标记，
 * 同步调整 Redis 叫号队列 ZSet 的 score（详见 {@code TriageService} 的 score 注释）。
 */
@Data
public class TriageSetPriorityDTO {

    /** 签到记录 ID */
    @NotNull(message = "签到记录ID不能为空")
    private Long checkinId;

    /** 分诊优先级：0-急诊 / 1-优先 / 2-普通 */
    @NotNull(message = "优先级不能为空")
    @Min(value = 0, message = "优先级取值仅支持 0/1/2")
    @Max(value = 2, message = "优先级取值仅支持 0/1/2")
    private Integer priority;

    /** 回诊标记：0-否 / 1-是（检查检验完成回诊，同档插队） */
    @Min(value = 0, message = "回诊标记仅支持 0/1")
    @Max(value = 1, message = "回诊标记仅支持 0/1")
    private Integer returnFlag;
}
