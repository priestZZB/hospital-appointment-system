package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 术前评估 DTO（迭代10 F3；ASA Ⅰ~Ⅴ 以数字字符 1~5 提交，重复提交覆盖更新） */
@Data
public class PreopAssessmentDTO {
    /** ASA 分级 1~5（Ⅰ~Ⅴ 用数字字符存储） */
    @NotBlank(message = "ASA分级不能为空")
    @Pattern(regexp = "[1-5]", message = "ASA分级必须为1~5")
    private String asaGrade;
    @Size(max = 500, message = "风险因素不能超过500字")
    private String riskFactors;
    @Size(max = 1000, message = "评估内容不能超过1000字")
    private String assessmentText;
    /** PASSED-通过 / CONDITIONAL-有条件通过 / REJECTED-不通过 */
    @NotBlank(message = "评估结论不能为空")
    @Pattern(regexp = "PASSED|CONDITIONAL|REJECTED", message = "评估结论必须为 PASSED/CONDITIONAL/REJECTED")
    private String conclusion;
}
