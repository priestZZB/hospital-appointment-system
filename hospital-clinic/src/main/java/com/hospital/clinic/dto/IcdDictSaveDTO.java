package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ICD-10 诊断字典新增/编辑请求 DTO（迭代9 J3）
 */
@Data
public class IcdDictSaveDTO {

    /** ICD-10 编码（唯一） */
    @NotBlank(message = "ICD编码不能为空")
    @Size(max = 20, message = "ICD编码长度不能超过20")
    private String icdCode;

    /** 诊断标准名称（中文规范名） */
    @NotBlank(message = "诊断名称不能为空")
    @Size(max = 200, message = "诊断名称长度不能超过200")
    private String icdName;

    /** ICD 章节分类（如 循环系统疾病） */
    @Size(max = 50, message = "章节分类长度不能超过50")
    private String category;

    /** 常用标记：0-普通 / 1-常用（默认 0） */
    private Integer isCommon;

    /** 状态：1-启用 / 0-停用（默认 1） */
    private Integer status;
}
