package com.hospital.auth.dto;

import com.hospital.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQueryDTO extends PageDTO {

    /** 手机号（模糊搜索） */
    private String phone;

    /** 用户类型：PATIENT / ADMIN */
    private String userType;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}
