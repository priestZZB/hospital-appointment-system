package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 转科 DTO（E1） */
@Data
public class TransferDeptDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotNull(message = "转入科室不能为空")
    private Long targetDeptId;
    private String reason;
}
