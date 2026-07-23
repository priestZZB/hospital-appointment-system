package com.hospital.clinic.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约订单 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentVO {

    private Long id;
    private String appointmentNo;
    private Long patientId;
    private String patientName;
    private Long slotId;
    private Long scheduleId;
    private Long doctorId;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private LocalDate appointmentDate;
    private String period;
    private Integer slotSeq;
    private LocalTime slotStart;
    private LocalTime slotEnd;
    private BigDecimal registerFee;
    private String orderStatus;
    private String visitStatus;
    private Long paymentOrderId;
    private String paymentOrderNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
