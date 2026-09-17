package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 入院登记实体 */
@Data
public class Admission implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String admissionNo;
    private Long patientId;
    private Long departmentId;
    /** 主治医生用户ID（auth_db.user.id） */
    private Long attendingDoctorId;
    /** 主治医生姓名（冗余，列表展示免跨服务查询） */
    private String attendingDoctorName;
    private String admissionDiag;
    private Integer expectedDays;
    private LocalDateTime admissionTime;
    /** ADMITTED-在院 / DISCHARGED-已出院 */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
