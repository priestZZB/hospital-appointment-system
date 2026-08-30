package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收费员日结单表实体
 *
 * @see <a href="classpath:db/migration/V6__cashier_refund_settle.sql">settle_record 表 DDL</a>
 */
@Data
public class SettleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 日结单编号 */
    private String settleNo;

    /** 收费员ID（关联 auth_db.user.id） */
    private Long cashierId;

    /** 结算日期 */
    private LocalDate settleDate;

    /** 结算总金额 */
    private BigDecimal totalAmount;

    /** 订单笔数 */
    private Integer orderCount;

    /** 分类汇总JSON（按订单类型/支付方式） */
    private String detail;

    /** 创建时间 */
    private LocalDateTime createTime;
}
