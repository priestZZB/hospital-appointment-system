package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检验结果明细表实体（迭代8 C3）
 * <p>
 * 挂 exam_report.id，化验单按行渲染；abnormal_flag 由 Service 依据
 * result_value 与 ref_range 判定：高于上限记 ↑，低于下限记 ↓，正常/解析失败留空。
 *
 * @see V9__lis_pacs.sql — result_item 表 DDL
 */
@Data
public class ResultItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 exam_report.id */
    private Long reportId;

    /** 项目代号（如 WBC） */
    private String itemCode;

    /** 项目名称（如 白细胞计数） */
    private String itemName;

    /** 结果值（如 12.3） */
    private String resultValue;

    /** 单位（如 10⁹/L） */
    private String unit;

    /** 参考范围（如 3.5-9.5 或 3.5-9.5×10⁹/L） */
    private String refRange;

    /** 异常标志: ↑-高于上限 / ↓-低于下限 / 正常留空 */
    private String abnormalFlag;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;
}
