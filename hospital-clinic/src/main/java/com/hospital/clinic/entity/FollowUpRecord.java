package com.hospital.clinic.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 随访记录实体 */
@Data
public class FollowUpRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long planId;
    private Long patientId;
    private Long doctorId;
    private String content;
    private LocalDate nextFollowDate;
    private LocalDateTime createTime;
}
