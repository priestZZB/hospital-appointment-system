package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 知情同意书 VO（迭代10 F3） */
@Data
public class InformedConsentVO {
    private Long id;
    private Long surgeryId;
    /** SURGERY-手术同意书 / ANESTHESIA-麻醉同意书 */
    private String consentType;
    /** 是否已签署（存在记录即视为已签） */
    private Boolean signed;
    private String patientSign;
    private LocalDateTime signedTime;
    private String witness;
    private LocalDateTime createTime;
}
