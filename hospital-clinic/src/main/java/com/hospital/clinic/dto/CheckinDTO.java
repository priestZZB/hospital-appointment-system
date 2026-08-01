package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 签到请求 DTO
 */
@Data
public class CheckinDTO {

    @NotNull(message = "预约ID不能为空")
    private Long appointmentId;
}
