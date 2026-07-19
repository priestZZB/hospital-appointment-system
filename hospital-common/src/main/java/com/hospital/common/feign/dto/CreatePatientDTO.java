package com.hospital.common.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建患者档案 Feign DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePatientDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联用户 ID */
    private Long userId;

    /** 患者姓名 */
    private String name;

    /** 联系电话 */
    private String phone;
}
