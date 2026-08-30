package com.hospital.auth.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息 VO
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 手机号 */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 用户类型 */
    private String userType;

    /** 主岗位 ID（position.id） */
    private Long positionId;

    /** 岗位名称（冗余展示，如：内科·科主任） */
    private String positionName;

    /** 岗位职务（如：科主任/主治医师） */
    private String positionTitle;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 角色编码列表 */
    private List<String> roles;
}
