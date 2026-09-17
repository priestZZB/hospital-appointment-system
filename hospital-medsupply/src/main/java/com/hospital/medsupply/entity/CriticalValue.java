package com.hospital.medsupply.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 危急值实体 */
@Data
public class CriticalValue implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long reportId;
    private Long applicationId;
    private Long patientId;
    private String itemName;
    private String resultValue;
    private String referenceRange;
    /** HIGH-高 / LOW-低 */
    private String criticalLevel;
    /** PENDING-待复核 / CONFIRMED-已复核 / RESOLVED-已处置 */
    private String status;
    private Long reporterId;
    private Long confirmDoctorId;
    private String confirmComment;
    private LocalDateTime confirmTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
