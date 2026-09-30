package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 退号改期请求 DTO（迭代9 A3）
 * <p>
 * 将未就诊预约改期到同科室另一号源：旧号源释放、新号源占用、
 * 预约记录号源信息更新；原挂号费支付状态不变（不重复收费）。
 */
@Data
public class RescheduleDTO {

    /** 新号源 ID */
    @NotNull(message = "新号源ID不能为空")
    private Long newSlotId;

    /** 新排班 ID */
    @NotNull(message = "新排班ID不能为空")
    private Long newScheduleId;
}
