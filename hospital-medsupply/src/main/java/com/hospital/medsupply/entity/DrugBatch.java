package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 药品批次表实体（药库维度）
 * <p>
 * 采购入库/退药回冲/调拨入均落批次，支持效期预警与批次级扣减。
 *
 * @see V8__pharmacy_extension.sql — drug_batch 表 DDL
 */
@Data
public class DrugBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 drug.id */
    private Long drugId;

    /** 批号 */
    private String batchNo;

    /** 供货商 */
    private String supplier;

    /** 批次数量 */
    private Integer quantity;

    /** 生产日期 */
    private LocalDate productionDate;

    /** 失效日期 */
    private LocalDate expiryDate;

    /** 入库类型: PURCHASE-采购 / RETURN-退药回冲 / TRANSFER-调拨入 */
    private String inboundType;

    /** 状态: ACTIVE-在库 / EXHAUSTED-耗尽 / SCRAPPED-报损 */
    private String status;

    /** 操作人ID */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
