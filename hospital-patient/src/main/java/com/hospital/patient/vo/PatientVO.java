package com.hospital.patient.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者档案 VO
 */
@Data
@Builder
public class PatientVO {
    private Long id;
    private Long userId;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String idCard;
    private String phone;
    private String emergencyContact;
    private String emergencyPhone;
    private Integer verifyStatus;
    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String verifyComment;
    private String avatarUrl;
    private LocalDateTime createTime;
}
