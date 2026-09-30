package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 统一手术单 VO（迭代10） */
@Data
public class SurgeryVO {
    private Long id;
    /** SR + yyyyMMdd + 6 位流水 */
    private String surgeryNo;
    /** OUTPATIENT-门诊 / INPATIENT-住院 */
    private String source;
    private Long patientId;
    /** 患者姓名（详情/列表按需补齐，Feign fail-open 可为空） */
    private String patientName;
    private Long applyDoctorId;
    private String surgeryName;
    private String surgeryType;
    private LocalDateTime scheduledTime;
    private String operatingRoom;
    private Long surgeonId;
    private String anesthesiaMethod;
    private Long anesthesiologistId;
    private Integer durationMin;
    /** APPLIED/SCHEDULED/PREOP_PASSED/IN_OPERATION/OPERATED/CANCELLED */
    private String status;
    private Long applyId;
    private Long appointmentId;
    private String notes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
