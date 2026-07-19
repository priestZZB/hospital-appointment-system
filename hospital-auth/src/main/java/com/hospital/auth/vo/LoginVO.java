package com.hospital.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应 VO
 */
@Data
@Builder
public class LoginVO {

    /** JWT Token */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 角色编码列表 */
    private List<String> roles;
}
