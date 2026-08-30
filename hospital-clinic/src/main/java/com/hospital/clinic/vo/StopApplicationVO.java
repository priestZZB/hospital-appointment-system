package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停诊申请 VO
 */
@Data
@Builder
public class StopApplicationVO {

    private Long id;
    private Long scheduleId;
    private Long doctorId;
    private String applyReason;
    private String status;
    private String chiefReviewStatus;
    private Long chiefReviewedBy;
    private String chiefReviewComment;
    private LocalDateTime chiefReviewTime;
    private String approveComment;
    private Long approvedBy;
    private LocalDateTime approveTime;
    private Integer affectedCount;
    private BigDecimal refundTotal;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
