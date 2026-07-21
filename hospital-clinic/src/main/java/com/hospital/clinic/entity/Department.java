package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 科室表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">department 表 DDL</a>
 */
@Data
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 科室名称 */
    private String deptName;

    /** 科室编码 */
    private String deptCode;

    /** 科室简介 */
    private String description;

    /** 所在诊区/楼层 */
    private String location;

    /** 科室联系电话 */
    private String phone;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
