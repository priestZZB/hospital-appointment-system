package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 手术申请实体（迭代6 建单，迭代10 排台执行） */
@Data
public class SurgeryApply implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private Long patientId;
    private String surgeryName;
    /** 全麻/局麻/椎管内/神经阻滞 */
    private String anesthesiaType;
    private Long applyDoctorId;
    private LocalDateTime scheduledTime;
    private String operatingRoom;
    /** PENDING-待安排 / SCHEDULED-已排台 / COMPLETED-已完成 / CANCELLED-已取消 */
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
