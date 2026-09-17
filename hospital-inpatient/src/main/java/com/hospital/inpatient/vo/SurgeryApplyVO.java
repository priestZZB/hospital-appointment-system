package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 手术申请 VO */
@Data
public class SurgeryApplyVO {
    private Long id;
    private Long admissionId;
    private String admissionNo;
    private Long patientId;
    private String surgeryName;
    private String anesthesiaType;
    private Long applyDoctorId;
    private LocalDateTime scheduledTime;
    private String operatingRoom;
    private String status;
    private String remark;
    private LocalDateTime createTime;
}
