package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 手术申请 DTO */
@Data
public class SurgeryApplyDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotBlank(message = "手术名称不能为空")
    private String surgeryName;
    private String anesthesiaType;
    private String remark;
}
