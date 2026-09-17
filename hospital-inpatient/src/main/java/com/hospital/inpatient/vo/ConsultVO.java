package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 住院院内会诊 VO */
@Data
public class ConsultVO {
    private Long id;
    private Long admissionId;
    private String admissionNo;
    private Long patientId;
    private Long requestDeptId;
    private Long requestDoctorId;
    private Long targetDeptId;
    private Long targetDoctorId;
    private String reason;
    private String opinion;
    private String status;
    private Long handleDoctorId;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
