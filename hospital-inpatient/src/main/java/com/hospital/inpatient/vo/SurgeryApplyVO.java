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
    /** 排台成功后生成的统一手术单 id（迭代10 F2 打通，旧调用方可忽略） */
    private Long surgeryId;
}
