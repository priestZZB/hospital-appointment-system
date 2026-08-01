package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到记录 VO
 */
@Data
@Builder
public class CheckinVO {

    private Long id;
    private Long appointmentId;
    private Long patientId;
    private Long departmentId;
    private Long doctorId;
    private LocalDateTime checkinTime;
    private String queueStatus;
    private LocalDateTime callTime;
    private Integer callCount;
    private String consultRoom;
    private LocalDateTime createTime;
}
