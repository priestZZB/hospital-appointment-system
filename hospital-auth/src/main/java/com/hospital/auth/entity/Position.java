package com.hospital.auth.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位实体（迭代 5 阶段 2）
 * <p>
 * 岗位 = 部门 + 职务，纯展示、不参与权限；
 * {@code department_id} 关联 clinic_db.department.id（应用层引用，跨库不做外键），
 * 同时冗余 {@code department_name} 便于 auth_db 直接展示。
 */
@Data
public class Position implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 岗位编码 */
    private String positionCode;

    /** 岗位名称（部门·职务，如：内科·科主任） */
    private String positionName;

    /** 所属部门 ID（clinic.department.id，应用层引用） */
    private Long departmentId;

    /** 所属部门名称冗余 */
    private String departmentName;

    /** 职务：科主任/主治医师/护师/药师/收费员/管理员等 */
    private String title;

    /** 岗位描述 */
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}