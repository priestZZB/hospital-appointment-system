package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 手术排台看板行 VO（迭代10 F5 看板；含患者姓名与术前评估结论） */
@Data
public class SurgeryBoardVO {
    private Long id;
    private String surgeryNo;
    private String surgeryName;
    private String surgeryType;
    private Long patientId;
    /** 患者姓名（Feign 批量补齐，fail-open 可为空） */
    private String patientName;
    /** OUTPATIENT-门诊 / INPATIENT-住院 */
    private String source;
    private Long surgeonId;
    private String anesthesiaMethod;
    private String operatingRoom;
    private LocalDateTime scheduledTime;
    private String status;
    /** 术前评估结论 PASSED/CONDITIONAL/REJECTED，未评估为 null */
    private String preopConclusion;
    /** ASA 分级 1~5，未评估为 null */
    private String asaGrade;
}
