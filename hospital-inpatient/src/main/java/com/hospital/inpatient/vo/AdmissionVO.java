package com.hospital.inpatient.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院信息 VO */
@Data
public class AdmissionVO {
    private Long id;
    private String admissionNo;
    private Long patientId;
    private String patientName;
    private Long departmentId;
    private Long attendingDoctorId;
    private String attendingDoctorName;
    private String admissionDiag;
    private Integer expectedDays;
    private LocalDateTime admissionTime;
    private String status;
    /** 当前占用的床位（可能为空=未分床） */
    private Long currentBedId;
    private String currentRoomNo;
    private String currentBedNo;
    /** 预交金余额 */
    private BigDecimal depositBalance;
    /** 累计费用 */
    private BigDecimal totalFee;
    private LocalDateTime createTime;
}
