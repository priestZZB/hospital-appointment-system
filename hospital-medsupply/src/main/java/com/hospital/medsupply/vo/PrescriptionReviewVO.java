package com.hospital.medsupply.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 处方点评 VO（含患者/药师） */
@Data
public class PrescriptionReviewVO {
    private Long id;
    private Long prescriptionId;
    private Long patientId;
    private Long pharmacistId;
    private String pharmacistName;
    private String rating;
    private String problemType;
    private String comment;
    private LocalDateTime createTime;
}
