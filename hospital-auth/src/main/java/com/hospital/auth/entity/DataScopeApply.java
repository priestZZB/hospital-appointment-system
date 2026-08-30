package com.hospital.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 跨科室数据范围申请表实体（迭代 5 阶段 3）
 * <p>
 * 用户需查看非默认范围（本科室/本人）的数据时提交申请，
 * 由科主任/管理员/超管审批；APPROVED 行即临时/长期授权记录。
 */
@Data
public class DataScopeApply implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 申请人 ID（auth.user.id） */
    private Long userId;

    /** 申请人姓名（冗余） */
    private String userName;

    /** 目标科室 ID（clinic.department.id） */
    private Long targetDepartmentId;

    /** 目标科室名称（冗余） */
    private String targetDepartmentName;

    /** 申请理由 */
    private String reason;

    /** 状态：PENDING-待审批 / APPROVED-已通过 / REJECTED-已驳回 */
    private String status;

    /** 审批人 ID */
    private Long approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 审批意见 */
    private String approveComment;

    /** 授权过期时间（NULL=长期） */
    private LocalDateTime expireTime;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}