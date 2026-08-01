package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 停诊申请请求 DTO
 */
@Data
public class StopApplicationDTO {

    /** 排班ID */
    @NotNull(message = "排班ID不能为空")
    private Long scheduleId;

    /** 停诊原因 */
    @NotBlank(message = "停诊原因不能为空")
    private String applyReason;
}
