package com.hospital.patient.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 虚拟就诊卡表实体
 */
@Data
public class VisitCard implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;
    private String cardNo;
    private Integer status;
    private LocalDate issueDate;
    private LocalDateTime createTime;
}
