package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检查检验项目表实体
 *
 * @see V1__init.sql — exam_item 表 DDL
 */
@Data
public class ExamItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 项目类型：LAB-检验 / RADIOLOGY-放射 / ULTRASOUND-超声 / ENDOSCOPY-内镜 / ECG-心电 */
    private String itemType;

    /** 参考价格 */
    private BigDecimal referencePrice;

    /** 执行科室 */
    private String execDept;

    /** 注意事项 */
    private String precautions;

    /** 1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
