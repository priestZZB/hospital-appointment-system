package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 知情同意签署 DTO（迭代10 F3；SURGERY+ANESTHESIA 各一条，重复签署覆盖更新） */
@Data
public class InformedConsentDTO {
    /** SURGERY-手术同意书 / ANESTHESIA-麻醉同意书 */
    @NotBlank(message = "同意书类型不能为空")
    @Pattern(regexp = "SURGERY|ANESTHESIA", message = "同意书类型必须为 SURGERY 或 ANESTHESIA")
    private String consentType;
    /** 模拟签名文本（姓名+"（已签署）"+时间戳，后端拼装） */
    @NotBlank(message = "患者签名不能为空")
    @Size(max = 100, message = "患者签名不能超过100字")
    private String patientSign;
    @Size(max = 100, message = "见证人不能超过100字")
    private String witness;
}
