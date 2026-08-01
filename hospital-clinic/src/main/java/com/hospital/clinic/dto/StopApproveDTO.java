package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 停诊审批请求 DTO
 */
@Data
public class StopApproveDTO {

    /** 审批动作：APPROVE-通过 / REJECT-驳回 */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    /** 审批意见 */
    private String approveComment;
}
