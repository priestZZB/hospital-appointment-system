package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 护理病历录入 DTO */
@Data
public class NursingRecordDTO {
    @NotNull(message = "入院记录不能为空")
    private Long admissionId;
    /** ROUTINE-护理记录 / IO-出入量，缺省 ROUTINE */
    private String recordType;
    private String content;
    private Integer intakeMl;
    private Integer outputMl;
}
