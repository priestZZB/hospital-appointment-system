package com.hospital.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表实体
 *
 * @see <a href="classpath:db/migration/V1__init.sql">user 表 DDL</a>
 */
@Data
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 手机号（登录账号） */
    private String phone;

    /** BCrypt 加密密码 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 用户类型：PATIENT / ADMIN */
    private String userType;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最近登录 IP */
    private String lastLoginIp;

    /** 首次登录强制修改密码：0-否 1-是 */
    private Integer needPasswordChange;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 1-已删除 */
    private Integer deleted;
}
