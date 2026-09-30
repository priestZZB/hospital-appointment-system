package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 统一手术单排台 DTO（迭代10 F2；要求 surgery.status = APPLIED） */
@Data
public class SurgeryScheduleDTO {
    @NotNull(message = "排台时间不能为空")
    private LocalDateTime scheduledTime;
    @NotBlank(message = "手术间不能为空")
    @Size(max = 50, message = "手术间不能超过50字")
    private String operatingRoom;
    @NotNull(message = "主刀医生不能为空")
    private Long surgeonId;
    /** 全身麻醉/椎管内麻醉/神经阻滞/局部麻醉 */
    @NotBlank(message = "麻醉方式不能为空")
    private String anesthesiaMethod;
    /** 麻醉医生 userId（可空） */
    private Long anesthesiologistId;
}
