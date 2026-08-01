package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 排队状态 VO
 */
@Data
@Builder
public class QueueStatusVO {

    /** 签到ID */
    private Long checkinId;

    /** 科室名称 */
    private String deptName;

    /** 当前队列总人数 */
    private Long totalWaiting;

    /** 前面等待人数 */
    private Long aheadCount;

    /** 队列状态 */
    private String queueStatus;

    /** 签到时间 */
    private LocalDateTime checkinTime;
}
