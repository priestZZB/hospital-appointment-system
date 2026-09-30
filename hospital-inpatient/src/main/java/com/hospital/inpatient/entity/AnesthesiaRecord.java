package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 麻醉记录实体（迭代10 F4；每单一条，UNIQUE(surgery_id)） */
@Data
public class AnesthesiaRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long surgeryId;
    /** 全身麻醉/椎管内麻醉/神经阻滞/局部麻醉 */
    private String method;
    /** 1~5（数字字符存储） */
    private String asaGrade;
    private LocalDateTime inductionTime;
    private LocalDateTime reversalTime;
    /** 术前/术中/术后三组 BP/HR/SpO2 的 JSON 文本 */
    private String vitalsJson;
    private Long anesthesiologistId;
    private String notes;
    private LocalDateTime createTime;
}
