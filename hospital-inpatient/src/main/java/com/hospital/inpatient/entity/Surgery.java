package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一手术单实体（迭代10 F1~F5）。
 * <p>
 * 双来源：OUTPATIENT-门诊手术（F1 门诊直接建单）/
 * INPATIENT-住院手术（F2 由 surgery_apply 排台自动生成）。
 * <p>
 * 状态机：APPLIED(已建单待排台) → SCHEDULED(已排台) → PREOP_PASSED(术前评估通过)
 * → IN_OPERATION(手术中) → OPERATED(手术完成)；APPLIED/SCHEDULED 可转 CANCELLED。
 */
@Data
public class Surgery implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    /** 单号 SR + yyyyMMdd + 6 位流水 */
    private String surgeryNo;
    /** OUTPATIENT-门诊 / INPATIENT-住院 */
    private String source;
    private Long patientId;
    /** 申请医生 auth userId */
    private Long applyDoctorId;
    private String surgeryName;
    /** 手术类型自由文本：清创/肿物切除/骨折内固定/阑尾切除 等 */
    private String surgeryType;
    private LocalDateTime scheduledTime;
    private String operatingRoom;
    /** 主刀医生 auth userId */
    private Long surgeonId;
    /** 全身麻醉/椎管内麻醉/神经阻滞/局部麻醉 */
    private String anesthesiaMethod;
    /** 麻醉医生 auth userId */
    private Long anesthesiologistId;
    private Integer durationMin;
    /** APPLIED/SCHEDULED/PREOP_PASSED/IN_OPERATION/OPERATED/CANCELLED */
    private String status;
    /** 关联住院 surgery_apply.id（门诊单为空） */
    private Long applyId;
    /** 关联门诊 appointment.id（住院单为空） */
    private Long appointmentId;
    private String notes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
