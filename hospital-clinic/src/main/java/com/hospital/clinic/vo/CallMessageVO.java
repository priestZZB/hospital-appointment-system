package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 叫号推送消息 VO
 */
@Data
@Builder
public class CallMessageVO {

    /** 消息类型：CALL_NUMBER / RECALL / MISSED */
    private String type;

    /** 科室ID */
    private Long deptId;

    /** 科室名称 */
    private String deptName;

    /** 医生姓名 */
    private String doctorName;

    /** 诊室号 */
    private String consultRoom;

    /** 被叫号患者姓名 */
    private String patientName;

    /** 排队序号 */
    private Integer queueNumber;

    /** 时间戳 */
    private Long timestamp;
}
