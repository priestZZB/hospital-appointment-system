package com.hospital.auth.dto;

import com.hospital.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 跨科室申请分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataScopeApplyQueryDTO extends PageDTO {

    /** 申请人 ID（本人列表/审批列表用） */
    private Long userId;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 目标科室 ID */
    private Long targetDepartmentId;
}