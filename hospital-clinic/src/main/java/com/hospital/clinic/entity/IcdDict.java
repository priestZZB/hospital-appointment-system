package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ICD-10 诊断字典实体
 *
 * @see <a href="classpath:db/migration/V10__outpatient_enhance.sql">icd_dict 表 DDL（迭代9 J3）</a>
 */
@Data
public class IcdDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** ICD-10 编码（唯一，如 I10.x05） */
    private String icdCode;

    /** 诊断标准名称（中文规范名） */
    private String icdName;

    /** ICD 章节分类（如 循环系统疾病） */
    private String category;

    /** 常用标记：0-普通 / 1-常用（录入联想优先展示） */
    private Integer isCommon;

    /** 状态：1-启用 / 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
