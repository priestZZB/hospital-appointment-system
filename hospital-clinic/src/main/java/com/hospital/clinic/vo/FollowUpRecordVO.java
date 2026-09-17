package com.hospital.clinic.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 随访记录 VO */
@Data
public class FollowUpRecordVO {
    private Long id;
    private Long planId;
    private Long patientId;
    private Long doctorId;
    private String doctorName;
    private String content;
    private LocalDate nextFollowDate;
    private LocalDateTime createTime;
}
