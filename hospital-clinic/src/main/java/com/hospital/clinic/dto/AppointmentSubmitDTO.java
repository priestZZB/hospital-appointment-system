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
}
