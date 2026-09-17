package com.hospital.medsupply.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 危急值 VO（含患者/报告信息） */
@Data
public class CriticalValueVO {
    private Long id;
    private Long reportId;
    private Long applicationId;
    private Long patientId;
    private String itemName;
    private String resultValue;
    private String referenceRange;
    private String criticalLevel;
    private String status;
    private Long reporterId;
    private String reporterName;
    private Long confirmDoctorId;
    private String confirmDoctorName;
    private String confirmComment;
    private LocalDateTime confirmTime;
    private LocalDateTime createTime;
}
