package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 住院院内会诊处理 DTO（接受/完成/拒绝） */
@Data
public class ConsultHandleDTO {
    @NotNull(message = "会诊单不能为空")
    private Long consultId;
    /** ACCEPTED / COMPLETED / REJECTED */
    @NotBlank(message = "处理动作不能为空")
    private String action;
    private String opinion;
}
