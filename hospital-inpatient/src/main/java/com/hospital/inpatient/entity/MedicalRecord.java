package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 病案实体（迭代12 I3，与病案首页 medical_record_home 区分） */
@Data
public class MedicalRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    /** MR+yyyyMMdd+6位流水 */
    private String recordNo;
    private Long patientId;
    private String patientName;
    private Long admissionId;
    private String diagnosis;
    /** IN_WARD 病区未归 | ARCHIVED 已归档 */
    private String archiveStatus;
    private LocalDateTime archiveTime;
    private LocalDateTime createTime;
}
