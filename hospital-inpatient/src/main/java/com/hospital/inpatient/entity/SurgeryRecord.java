package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 手术记录实体（迭代10 F4；每单一条，UNIQUE(surgery_id)） */
@Data
public class SurgeryRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long surgeryId;
    private Long surgeonId;
    private String incision;
    private String procedureText;
    private String findings;
    /** 0-未送病理 / 1-已送病理 */
    private Integer specimenFlag;
    private Integer bloodLossMl;
    private Integer durationMin;
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
