package com.hospital.inpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 手术记录 DTO（迭代10 F4；要求 surgery.status = IN_OPERATION，一单一条） */
@Data
public class SurgeryRecordDTO {
    @Size(max = 100, message = "切口描述不能超过100字")
    private String incision;
    @NotBlank(message = "手术过程不能为空")
    @Size(max = 2000, message = "手术过程不能超过2000字")
    private String procedureText;
    @Size(max = 1000, message = "术中所见不能超过1000字")
    private String findings;
    /** 是否送病理：0-否 1-是，缺省 0 */
    private Integer specimenFlag;
    private Integer bloodLossMl;
    /** 手术时长（分钟），回写主单 duration_min */
    private Integer durationMin;
}
