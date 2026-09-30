package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 挂号下单 DTO
 */
@Data
public class AppointmentSubmitDTO {

    @NotNull(message = "号源ID不能为空")
    private Long slotId;

    @NotNull(message = "排班ID不能为空")
    private Long scheduleId;

    /**
     * 号源通道（可选，迭代9 A4）：NORMAL-普通（默认）/ GREEN-绿色通道。
     * 传 GREEN 时校验该号源 channel_type 必须为 GREEN；不传保持既有行为，向后兼容。
     */
    private String channelType;
}
