package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 病案借阅实体（迭代12 I3） */
@Data
public class RecordBorrow implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long recordId;
    private Long borrowerId;
    private String borrowerName;
    private String purpose;
    private LocalDateTime borrowTime;
    private LocalDateTime expectReturnTime;
    private LocalDateTime returnTime;
    /** BORROWED | RETURNED */
    private String status;
}
