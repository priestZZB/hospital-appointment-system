package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 护理病历 VO */
@Data
public class NursingRecordVO {
    private Long id;
    private Long admissionId;
    private String recordType;
    private String content;
    private Integer intakeMl;
    private Integer outputMl;
    private Long nurseId;
    private LocalDateTime recordTime;
}
