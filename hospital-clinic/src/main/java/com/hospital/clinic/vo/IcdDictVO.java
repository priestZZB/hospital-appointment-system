package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ICD-10 诊断字典 VO（迭代9 J3）
 */
@Data
@Builder
public class IcdDictVO {

    private Long id;

    /** ICD-10 编码 */
    private String icdCode;

    /** 诊断标准名称（中文规范名） */
    private String icdName;

    /** ICD 章节分类 */
    private String category;

    /** 常用标记：0-普通 / 1-常用 */
    private Integer isCommon;

    /** 状态：1-启用 / 0-停用 */
    private Integer status;

    private LocalDateTime createTime;
}
