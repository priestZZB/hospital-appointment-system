package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 麻醉记录 DTO（迭代10 F4；一单一条，vitals 为术前/术中/术后三组生命体征 JSON 字符串） */
@Data
public class AnesthesiaRecordDTO {
    /** 全身麻醉/椎管内麻醉/神经阻滞/局部麻醉 */
    @NotBlank(message = "麻醉方式不能为空")
    @Size(max = 50, message = "麻醉方式不能超过50字")
    private String method;
    /** ASA 分级 1~5（数字字符） */
    @Pattern(regexp = "[1-5]", message = "ASA分级必须为1~5")
    private String asaGrade;
    private LocalDateTime inductionTime;
    private LocalDateTime reversalTime;
    /** 术前/术中/术后 BP/HR/SpO2 的 JSON 文本（轻量模拟，原样落 CLOB） */
    @Size(max = 4000, message = "生命体征JSON过长")
    private String vitals;
    /** 麻醉医生 userId；为空取排台时的麻醉医生 */
    private Long anesthesiologistId;
    @Size(max = 500, message = "备注不能超过500字")
    private String notes;
}
