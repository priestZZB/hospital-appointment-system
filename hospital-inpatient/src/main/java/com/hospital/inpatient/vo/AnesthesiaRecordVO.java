package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 麻醉记录 VO（迭代10 F4；vitals 为术前/术中/术后三组生命体征 JSON 字符串） */
@Data
public class AnesthesiaRecordVO {
    private Long id;
    private Long surgeryId;
    private String method;
    private String asaGrade;
    private LocalDateTime inductionTime;
    private LocalDateTime reversalTime;
    /** JSON 文本原样返回 */
    private String vitals;
    private Long anesthesiologistId;
    private String notes;
    private LocalDateTime createTime;
}
