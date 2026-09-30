package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收费项目调价历史实体（迭代11 H4）
 *
 * @see <a href="classpath:db/migration/V9__insurance_charge.sql">charge_item_adjust_log 表 DDL</a>
 */
@Data
public class ChargeItemAdjustLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 收费项目 ID（charge_item.id） */
    private Long itemId;

    /** 调价前单价 */
    private BigDecimal oldPrice;

    /** 调价后单价 */
    private BigDecimal newPrice;

    /** 调价原因 */
    private String reason;

    /** 操作人 ID（auth userId） */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
