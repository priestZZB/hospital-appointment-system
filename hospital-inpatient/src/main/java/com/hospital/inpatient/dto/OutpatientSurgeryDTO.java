package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 门诊手术建单 DTO（迭代10 F1；source=OUTPATIENT，status=APPLIED，建单即生成手术单号；排台统一走 PUT /{id}/schedule） */
@Data
public class OutpatientSurgeryDTO {
    @NotNull(message = "患者ID不能为空")
    private Long patientId;
    @NotBlank(message = "手术名称不能为空")
    @Size(max = 200, message = "手术名称不能超过200字")
    private String surgeryName;
    /** 手术类型：清创/肿物切除/骨折内固定/阑尾切除 等（自由文本） */
    @Size(max = 50, message = "手术类型不能超过50字")
    private String surgeryType;
    /** 申请医生 userId；为空取当前登录医生 */
    private Long applyDoctorId;
    /** 门诊预约 id（可空，预留门诊 appointment 关联） */
    private Long appointmentId;
    @Size(max = 500, message = "备注不能超过500字")
    private String notes;
}
