package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品目录表实体
 *
 * @see V1__init.sql — drug 表 DDL
 */
@Data
public class Drug implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 药品编码 */
    private String drugCode;

    /** 药品名称（商品名） */
    private String drugName;

    /** 通用名 */
    private String genericName;

    /** 规格（如 0.25g×12片/盒） */
    private String specification;

    /** 剂型：TABLET-片剂 / CAPSULE-胶囊 / INJECTION-注射液 / SYRUP-糖浆 */
    private String dosageForm;

    /** 生产厂家 */
    private String manufacturer;

    /** 参考价格 */
    private BigDecimal referencePrice;

    /** 单位 */
    private String unit;

    /** 药品描述 */
    private String description;

    /** 1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
