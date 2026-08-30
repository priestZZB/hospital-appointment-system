package com.hospital.auth.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 跨科室数据范围申请 VO
 */
@Data
public class DataScopeApplyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 申请人 ID */
    private Long userId;

    /** 申请人姓名 */
    private String userName;

    /** 目标科室 ID */
    private Long targetDepartmentId;

    /** 目标科室名称 */
    private String targetDepartmentName;

    /** 申请理由 */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 审批人 ID */
    private Long approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 审批意见 */
    private String approveComment;

    /** 授权过期时间 */
    private LocalDateTime expireTime;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 审批时间 */
    private LocalDateTime approveTime;
}