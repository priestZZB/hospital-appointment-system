package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 入院登记 DTO */
@Data
public class AdmissionCreateDTO {
    @NotNull(message = "患者不能为空")
    private Long patientId;
    @NotNull(message = "收治科室不能为空")
    private Long departmentId;
    @NotNull(message = "主治医生不能为空")
    private Long attendingDoctorId;
    /** 主治医生姓名（冗余展示） */
    private String attendingDoctorName;
    @NotBlank(message = "入院诊断不能为空")
    private String admissionDiag;
    private Integer expectedDays;
}
