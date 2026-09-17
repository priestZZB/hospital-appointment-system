package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 住院院内会诊实体 */
@Data
public class InpatientConsult implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private Long patientId;
    private Long requestDeptId;
    private Long requestDoctorId;
    private Long targetDeptId;
    /** 空 = 科室会诊 */
    private Long targetDoctorId;
    private String reason;
    private String opinion;
    /** PENDING / ACCEPTED / COMPLETED / REJECTED */
    private String status;
    private Long handleDoctorId;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
