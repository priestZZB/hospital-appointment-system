package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 知情同意书实体（迭代10 F3；SURGERY-手术同意书 / ANESTHESIA-麻醉同意书，每单各一条） */
@Data
public class InformedConsent implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long surgeryId;
    /** SURGERY / ANESTHESIA */
    private String consentType;
    /** 模拟签名：姓名 + "（已签署）" + 时间戳文本 */
    private String patientSign;
    private LocalDateTime signedTime;
    private String witness;
    private LocalDateTime createTime;
}
