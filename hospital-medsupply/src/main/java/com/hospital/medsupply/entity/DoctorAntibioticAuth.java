package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 医生抗菌药物分级授权实体
 * <p>
 * 一名医生一条授权记录（doctor_id 唯一），max_level 为授权上限分级。
 *
 * @see V8__pharmacy_extension.sql — doctor_antibiotic_auth 表 DDL
 */
@Data
public class DoctorAntibioticAuth implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 clinic_db.doctor.id（应用层引用） */
    private Long doctorId;

    /** 授权上限分级: NON_RESTRICTED-非限制使用 / RESTRICTED-限制使用 / SPECIAL-特殊使用 */
    private String maxLevel;

    /** 审批人ID */
    private Long approverId;

    /** 1-有效 0-已撤销 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
