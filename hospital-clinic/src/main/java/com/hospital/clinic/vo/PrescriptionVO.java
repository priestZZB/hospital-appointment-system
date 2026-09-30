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
    /** 处方类型：WESTERN-西药/中成药处方笺 / HERBAL-中药饮片处方笺（V9） */
    private String prescriptionType;
    /** 中药剂数（HERBAL 处方必填）（V9） */
    private Integer herbalDoses;
    /** 煎服法（V9） */
    private String herbalUsage;
    private List<PrescriptionItemVO> items;
    private LocalDateTime createTime;
}
