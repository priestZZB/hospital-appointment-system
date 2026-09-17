package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 出院小结 DTO */
@Data
public class DischargeDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotBlank(message = "出院诊断不能为空")
    private String dischargeDiag;
    private String treatmentProcess;
    private String dischargeCondition;
    private String dischargeAdvice;
}
