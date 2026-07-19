package com.hospital.patient.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者电子档案表实体
 */
@Data
public class Patient implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String idCard;
    private String phone;
    private String emergencyContact;
    private String emergencyPhone;
    /** 实名认证：0-未认证 1-审核中 2-已认证 3-认证驳回 */
    private Integer verifyStatus;
    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String verifyComment;
    private String avatarUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
