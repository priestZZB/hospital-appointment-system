package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 叫号请求 DTO
 */
@Data
public class CallNextDTO {

    @NotNull(message = "科室ID不能为空")
    private Long departmentId;

    @NotBlank(message = "诊室号不能为空")
    private String consultRoom;
}
