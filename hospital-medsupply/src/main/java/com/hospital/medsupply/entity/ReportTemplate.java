package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告模板字典实体（D4 报告模板：所见/印象，按类别与部位）
 * <p>
 * 医生开影像报告时可按 modality + body_part 套用所见/印象模板；
 * body_part 为空表示通用模板，精确匹配优先于通用。
 *
 * @see V9__lis_pacs.sql — report_template 表 DDL
 */
@Data
public class ReportTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 检查类别: DX/CT/MR/US/ENDO/PATH/ECG */
    private String modality;

    /** 部位（空 = 通用模板） */
    private String bodyPart;

    /** 模板类型: FINDING-所见 / CONCLUSION-印象 */
    private String templateType;

    /** 模板内容 */
    private String content;

    /** 1-启用 0-停用（删除为逻辑删除置 0） */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
