package com.hospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实名认证审核 DTO
 */
@Data
public class RealnameReviewDTO {

    /** 审核结果：2-通过 3-驳回 */
    @NotNull(message = "审核结果不能为空")
    private Integer verifyStatus;

    @NotBlank(message = "审核意见不能为空")
    private String verifyComment;
}
