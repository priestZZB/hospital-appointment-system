package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 术前评估 VO（迭代10 F3） */
@Data
public class PreopAssessmentVO {
    private Long id;
    private Long surgeryId;
    /** 1~5（Ⅰ~Ⅴ） */
    private String asaGrade;
    private String riskFactors;
    private String assessmentText;
    /** PASSED/CONDITIONAL/REJECTED */
    private String conclusion;
    private Long assessorId;
    private LocalDateTime assessmentTime;
    private LocalDateTime createTime;
}
