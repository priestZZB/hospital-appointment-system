package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 药品库存表实体
 * <p>
 * 发药时使用乐观锁扣减库存（version 字段）。
 *
 * @see V1__init.sql — drug_inventory 表 DDL
 */
@Data
public class DrugInventory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 drug.id */
    private Long drugId;

    /** 当前库存量 */
    private Integer currentStock;

    /** 库存最低阈值 */
    private Integer minThreshold;

    /** 乐观锁版本号 */
    private Integer version;

    /** 最近入库时间 */
    private LocalDateTime lastStockinTime;

    /** 最近出库时间 */
    private LocalDateTime lastStockoutTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
