package com.hospital.common.feign.dto;

import lombok.Data;

import java.time.LocalDate;

/** 跨服务创建随访计划 DTO（住院出院 → clinic 随访，E4） */
@Data
public class CreateFollowUpDTO {
    private Long patientId;
    /** 随访医生 auth userId（用于解析 doctor 档案） */
    private Long doctorUserId;
    private LocalDate followDate;
    /** PHONE-电话 / VISIT-门诊复诊 / WECHAT-微信 / OTHER-其他 */
    private String followMethod;
    private String template;
}
