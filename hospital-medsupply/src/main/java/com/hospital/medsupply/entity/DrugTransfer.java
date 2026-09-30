package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 药品调拨单实体
 * <p>
 * 药库 → 药房 → 科室流转留痕，简化模式不拆批次。
 *
 * @see V8__pharmacy_extension.sql — drug_transfer 表 DDL
 */
@Data
public class DrugTransfer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 调拨单号 */
    private String transferNo;

    /** 关联 drug.id */
    private Long drugId;

    /** 调拨数量 */
    private Integer quantity;

    /** 调出位置: WAREHOUSE-药库 / PHARMACY-药房 / DEPT-临床科室 */
    private String fromLocation;

    /** 调入位置: WAREHOUSE-药库 / PHARMACY-药房 / DEPT-临床科室 */
    private String toLocation;

    /** 关联批号（可空） */
    private String batchNo;

    /** 操作人ID */
    private Long operatorId;

    /** 状态: COMPLETED-已完成 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
