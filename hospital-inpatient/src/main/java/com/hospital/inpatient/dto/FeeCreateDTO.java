package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 费用登记 DTO（床位费/诊疗费等入住院费用流水） */
@Data
public class FeeCreateDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
    /** BED / TREATMENT / DRUG / EXAM / LAB / NURSING / OTHER，缺省 OTHER */
    private String feeType;
    @NotBlank(message = "项目名称不能为空")
    private String itemName;
}
