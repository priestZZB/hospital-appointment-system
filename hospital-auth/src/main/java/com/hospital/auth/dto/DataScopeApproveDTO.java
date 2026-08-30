package com.hospital.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批跨科室数据范围申请 DTO
 */
@Data
public class DataScopeApproveDTO {

    /** 审批动作：APPROVE-通过 / REJECT-驳回 */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    /** 审批意见 */
    @Size(max = 500, message = "审批意见不能超过500个字符")
    private String approveComment;

    /** 授权过期时间（通过时可选；null=长期） */
    private String expireTime;
}