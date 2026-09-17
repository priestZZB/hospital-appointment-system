package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 住院院内会诊发起 DTO */
@Data
public class ConsultCreateDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotNull(message = "受邀科室不能为空")
    private Long targetDeptId;
    /** 受邀医生（可空 = 科室会诊） */
    private Long targetDoctorId;
    @NotBlank(message = "会诊目的不能为空")
    private String reason;
}
