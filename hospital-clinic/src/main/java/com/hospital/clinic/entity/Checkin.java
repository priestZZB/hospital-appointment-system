package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 签到记录表实体
 *
 * @see V1__init.sql — checkin 表 DDL
 */
@Data
public class Checkin implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 appointment.id（一对一） */
    private Long appointmentId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 科室ID（冗余） */
    private Long departmentId;

    /** 医生ID（冗余） */
    private Long doctorId;

    /** 签到时间 */
    private LocalDateTime checkinTime;

    /** WAITING-等待中 / CALLED-已叫号 / RE_CALLED-已重呼 / MISSED-过号 / IN_CONSULT-就诊中 */
    private String queueStatus;

    /** 最近一次叫号时间 */
    private LocalDateTime callTime;

    /** 叫号次数（含重呼） */
    private Integer callCount;

    /** 过号后重新排队时间 */
    private LocalDateTime rejoinTime;

    /** 诊室号 */
    private String consultRoom;

    /** 分诊优先级：0-急诊 / 1-优先 / 2-普通（迭代9 A1） */
    private Integer priority;

    /** 回诊标记：0-初诊排队 / 1-检查检验完成回诊（迭代9 A6） */
    private Integer returnFlag;

    /** 分诊操作护士用户 ID（auth sys_user.id，迭代9 A1） */
    private Long triageNurseId;

    /** 分诊操作时间（迭代9 A1） */
    private LocalDateTime triageTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
