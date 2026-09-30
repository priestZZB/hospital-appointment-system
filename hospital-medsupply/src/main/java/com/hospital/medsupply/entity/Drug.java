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

    /** 药品分类: WESTERN-西药 / CHINESE_PATENT-中成药 / HERBAL-中药饮片（V8 B1） */
    private String drugType;

    /** 管控级别: NORMAL-普通 / NARCOTIC-麻醉药品 / PSYCHIATRIC_1-第一类精神 / PSYCHIATRIC_2-第二类精神（V8 B8） */
    private String controlLevel;

    /** 抗菌分级: NULL-非抗菌 / NON_RESTRICTED-非限制使用 / RESTRICTED-限制使用 / SPECIAL-特殊使用（V8 B10） */
    private String antibioticLevel;

    /** 1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
