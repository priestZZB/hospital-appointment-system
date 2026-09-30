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

    /** 分诊优先级：0-急诊 / 1-优先 / 2-普通（迭代9 A1） */
    private Integer priority;

    /** 回诊标记：0-初诊排队 / 1-检查检验完成回诊（迭代9 A6） */
    private Integer returnFlag;

    private LocalDateTime createTime;
}
