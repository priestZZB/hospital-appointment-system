package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 占床记录实体（含转床历史） */
@Data
public class BedOccupancy implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long admissionId;
    private Long bedId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** ADMIT / TRANSFER / DISCHARGE */
    private String changeType;
    private LocalDateTime createTime;
}
