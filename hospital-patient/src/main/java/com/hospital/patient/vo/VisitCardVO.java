package com.hospital.patient.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 就诊卡 VO
 */
@Data
@Builder
public class VisitCardVO {
    private Long id;
    private String cardNo;
    private Integer status;
    private LocalDate issueDate;
}
