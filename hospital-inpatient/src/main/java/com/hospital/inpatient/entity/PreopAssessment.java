package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 术前评估实体（迭代10 F3；ASA Ⅰ~Ⅴ 以数字字符 1~5 存储） */
@Data
public class PreopAssessment implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long surgeryId;
    /** 1~5（数字字符存储） */
    private String asaGrade;
    private String riskFactors;
    private String assessmentText;
    /** PASSED-通过 / CONDITIONAL-有条件通过 / REJECTED-不通过 */
    private String conclusion;
    private Long assessorId;
    private LocalDateTime assessmentTime;
    private LocalDateTime createTime;
}
