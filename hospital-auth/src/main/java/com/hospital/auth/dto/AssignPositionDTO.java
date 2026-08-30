package com.hospital.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 给用户分配岗位 DTO
 */
@Data
public class AssignPositionDTO {

    /** 目标用户 ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 岗位 ID（null 表示撤销岗位） */
    private Long positionId;
}