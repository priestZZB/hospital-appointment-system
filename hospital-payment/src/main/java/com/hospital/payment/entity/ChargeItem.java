package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一收费项目目录实体（迭代11 H4）
 *
 * @see <a href="classpath:db/migration/V9__insurance_charge.sql">charge_item 表 DDL</a>
 */
@Data
public class ChargeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 项目编码（唯一，如 TREAT01/MAT01/SVC01） */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 类别：DIAGNOSIS-诊疗 / MATERIAL-耗材 / SERVICE-服务 / EXAM-检查 / LAB-检验 */
    private String category;

    /** 单位 */
    private String unit;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 价格状态：ACTIVE-启用 / ADJUSTED-已调价 / DEPRECATED-停用 */
    private String priceStatus;

    /** 调价说明 */
    private String adjustNote;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
