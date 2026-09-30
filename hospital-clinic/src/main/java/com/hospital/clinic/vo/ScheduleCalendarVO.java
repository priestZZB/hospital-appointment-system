package com.hospital.clinic.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 排班日历条目 VO（迭代9 A8）
 * <p>
 * 扁平列表形式（一行 = 某医生某日某时段的一条排班），
 * 前端可按 date × doctorId 自行装配日期×医生矩阵。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleCalendarVO {

    /** 排班 ID */
    private Long scheduleId;

    /** 出诊日期 */
    private LocalDate date;

    /** 医生 ID */
    private Long doctorId;

    /** 医生姓名 */
    private String doctorName;

    /** 科室 ID */
    private Long departmentId;

    /** 时段：AM-上午 / PM-下午 */
    private String period;

    /** 时段开始时间 */
    private LocalTime periodStart;

    /** 时段结束时间 */
    private LocalTime periodEnd;

    /** 排班是否已确认（CONFIRMED，可挂号） */
    private Boolean confirmed;

    /** 号源总数 */
    private Integer slotTotal;

    /** 剩余可用号源数 */
    private Integer slotAvailable;

    /** 号别：NORMAL-普通号 / EXPERT-专家号 */
    private String feeType;
}
