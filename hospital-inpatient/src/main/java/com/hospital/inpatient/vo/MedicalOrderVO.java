package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 医嘱 VO */
@Data
public class MedicalOrderVO {
    private Long id;
    private String orderNo;
    private Long admissionId;
    private Long doctorId;
    private String orderType;
    private String category;
    private String content;
    private String frequency;
    private String status;
    private LocalDateTime openTime;
    private LocalDateTime confirmTime;
    private LocalDateTime stopTime;
    private LocalDateTime createTime;
}
