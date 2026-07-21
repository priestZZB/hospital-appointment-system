package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 医生信息 VO
 */
@Data
@Builder
public class DoctorVO {

    private Long id;
    private Long userId;
    private String name;
    private Integer gender;
    private String phone;
    private Long departmentId;
    private String departmentName;
    private String title;
    private String specialty;
    private String introduction;
    private Integer status;
    private LocalDateTime createTime;
}
