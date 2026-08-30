package com.hospital.auth.dto;

import com.hospital.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PositionPageQueryDTO extends PageDTO {

    /** 关键字（岗位名称/编码模糊） */
    private String keyword;

    /** 部门 ID */
    private Long departmentId;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}