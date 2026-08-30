package com.hospital.auth.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位 VO
 */
@Data
public class PositionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 岗位编码 */
    private String positionCode;

    /** 岗位名称 */
    private String positionName;

    /** 所属部门 ID */
    private Long departmentId;

    /** 所属部门名称 */
    private String departmentName;

    /** 职务 */
    private String title;

    /** 岗位描述 */
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 排序号 */
    private Integer sortOrder;

    /** 在岗人数（分页列表冗余统计） */
    private Long userCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}