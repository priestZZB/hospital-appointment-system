package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存流水表实体
 * <p>
 * 每次入库、出库、盘点、发药操作均记录一条流水。
 *
 * @see V1__init.sql — drug_inventory_log 表 DDL
 */
@Data
public class DrugInventoryLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 drug.id */
    private Long drugId;

    /** 变更类型：IN-入库 / OUT-出库 / ADJUST-盘点调整 / DISPENSE-发药扣减 */
    private String changeType;

    /** 变更数量（正数为入库，负数为出库） */
    private Integer changeQuantity;

    /** 变更前库存 */
    private Integer beforeStock;

    /** 变更后库存 */
    private Integer afterStock;

    /** 关联单据ID */
    private Long relatedOrderId;

    /** 操作人ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 操作时间 */
    private LocalDateTime createTime;
}
