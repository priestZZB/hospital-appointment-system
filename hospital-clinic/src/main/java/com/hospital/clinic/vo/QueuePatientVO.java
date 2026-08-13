package com.hospital.clinic.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 科室排队患者 VO（签到叫号大屏 / 医生待接诊列表共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueuePatientVO {

    /** 签到记录 ID */
    private Long checkinId;

    /** 预约 ID */
    private Long appointmentId;

    /** 患者 ID */
    private Long patientId;

    /** 患者姓名（Feign 补充） */
    private String patientName;

    /** 科室 ID */
    private Long departmentId;

    /** 科室名称 */
    private String departmentName;

    /** 医生 ID */
    private Long doctorId;

    /** 医生姓名 */
    private String doctorName;

    /** 就诊日期 */
    private LocalDate appointmentDate;

    /** 上/下午 AM/PM */
    private String period;

    /** 号序 */
    private Integer slotSeq;

    /** 号源开始时间 */
    private LocalTime slotStart;

    /** 号源结束时间 */
    private LocalTime slotEnd;

    /** 排队状态：WAITING/CALLED/RE_CALLED/MISSED/IN_CONSULT */
    private String queueStatus;

    /** 叫号次数 */
    private Integer callCount;

    /** 诊室 */
    private String consultRoom;

    /** 就诊状态 */
    private String visitStatus;

    /** 签到时间 */
    private LocalDateTime checkinTime;

    /** 叫号时间 */
    private LocalDateTime callTime;
}
