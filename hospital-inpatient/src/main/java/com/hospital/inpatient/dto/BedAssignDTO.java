package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 分床 / 转床 DTO */
@Data
public class BedAssignDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotNull(message = "床位不能为空")
    private Long bedId;
}
