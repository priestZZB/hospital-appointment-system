package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 手术记录 VO（迭代10 F4） */
@Data
public class SurgeryRecordVO {
    private Long id;
    private Long surgeryId;
    private Long surgeonId;
    private String incision;
    private String procedureText;
    private String findings;
    /** 0-未送病理 1-已送病理 */
    private Integer specimenFlag;
    private Integer bloodLossMl;
    private Integer durationMin;
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
