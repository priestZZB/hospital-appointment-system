package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 护理病历实体（护理记录单/出入量） */
@Data
public class NursingRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    /** ROUTINE-护理记录 / IO-出入量 */
    private String recordType;
    private String content;
    private Integer intakeMl;
    private Integer outputMl;
    private Long nurseId;
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
