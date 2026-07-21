package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科室信息 VO
 */
@Data
@Builder
public class DepartmentVO {

    private Long id;
    private String deptName;
    private String deptCode;
    private String description;
    private String location;
    private String phone;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
