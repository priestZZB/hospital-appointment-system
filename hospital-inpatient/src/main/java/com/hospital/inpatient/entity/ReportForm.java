package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 传染病/不良事件上报登记实体（迭代12 I2） */
@Data
public class ReportForm implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    /** INFECTIOUS 传染病 | ADVERSE_EVENT 不良事件 */
    private String reportType;
    private Long patientId;
    private String patientName;
    private String eventName;
    private LocalDateTime eventTime;
    private String occurDepartment;
    private String content;
    private Long reporterId;
    private String reporterName;
    /** DRAFT | SUBMITTED | REVIEWED */
    private String status;
    private String reviewNote;
    private LocalDateTime createTime;
}
