package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保目录映射实体（迭代11 H1）
 *
 * @see <a href="classpath:db/migration/V9__insurance_charge.sql">insurance_catalog 表 DDL</a>
 */
@Data
public class InsuranceCatalog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 业务类型：REGISTER-挂号 / DRUG-药品 / EXAM-检查 / LAB-检验 / TREATMENT-诊疗 / MATERIAL-耗材 / CHARGED_ITEM-收费项目 */
    private String itemType;

    /** 引用业务 ID（药品/检查/收费项目 id，CHARGED_ITEM 时为 charge_item.id） */
    private Long itemRefId;

    /** 目录名称 */
    private String itemName;

    /** 目录分类：A-甲类 / B-乙类 / C-自费 */
    private String catalogClass;

    /** 比例(%)：甲类 100.00 / 乙类先行自付比例默认 15.00 / 自费 0（仅供核对，结算以 Service 常量为准） */
    private BigDecimal reimburseRatio;

    /** 状态：1-启用 / 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
