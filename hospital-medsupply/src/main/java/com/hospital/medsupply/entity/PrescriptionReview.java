package com.hospital.medsupply.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 处方点评实体 */
@Data
public class PrescriptionReview implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long prescriptionId;
    private Long patientId;
    private Long pharmacistId;
    /** REASONABLE-合理 / UNREASONABLE-不合理 */
    private String rating;
    private String problemType;
    private String comment;
    private LocalDateTime createTime;
}
