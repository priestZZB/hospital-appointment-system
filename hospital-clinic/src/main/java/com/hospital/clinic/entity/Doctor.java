package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 医生表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">doctor 表 DDL</a>
 */
@Data
public class Doctor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 auth_db.user.id（应用层引用） */
    private Long userId;

    /** 医生姓名 */
    private String name;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 联系电话 */
    private String phone;

    /** 所属科室 ID */
    private Long departmentId;

    /** 职称：CHIEF / VICE_CHIEF / ATTENDING / RESIDENT */
    private String title;

    /** 专长标签（逗号分隔） */
    private String specialty;

    /** 医生简介 */
    private String introduction;

    /** 状态：1-在职 0-离职/停诊 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
