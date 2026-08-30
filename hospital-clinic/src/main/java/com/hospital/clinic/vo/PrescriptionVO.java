package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方 VO
 */
@Data
@Builder
public class PrescriptionVO {

    private Long id;
    private String prescriptionNo;
    private Long medicalRecordId;
    private Long patientId;
    private Long doctorId;
    private String status;
    private String reviewComment;
    private BigDecimal totalAmount;
    private String payStatus;
    private List<PrescriptionItemVO> items;
    private LocalDateTime createTime;
}
