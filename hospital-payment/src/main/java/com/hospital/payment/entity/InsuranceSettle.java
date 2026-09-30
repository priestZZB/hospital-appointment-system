package com.hospital.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保结算单实体（迭代11 H2）
 *
 * @see <a href="classpath:db/migration/V9__insurance_charge.sql">insurance_settle 表 DDL</a>
 */
@Data
public class InsuranceSettle implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 结算单号（MI + yyyyMMdd + 6 位流水，seq_settle_no 取号） */
    private String settleNo;

    /** 患者档案 ID */
    private Long patientId;

    /** 医保号 */
    private String insuranceNo;

    /** 业务类型：REGISTER-挂号 / OUTPATIENT-门诊 / INPATIENT-住院 */
    private String bizType;

    /** 关联业务 ID（既有支付订单/结算单 id，可空） */
    private Long bizRefId;

    /** 总金额（三色金额之和） */
    private BigDecimal totalAmount;

    /** 甲类金额 */
    private BigDecimal catalogAAmount;

    /** 乙类金额 */
    private BigDecimal catalogBAmount;

    /** 纯自费金额 */
    private BigDecimal selfAmount;

    /** 医保统筹支付 */
    private BigDecimal insurancePay;

    /** 个人账户支付 */
    private BigDecimal personalAccountPay;

    /** 现金支付 */
    private BigDecimal cashAmount;

    /** 逐项拆分明细 JSON（itemName/itemType/catalogClass/amount/insurancePay/personalPay/firstSelfPay/note） */
    private String detailJson;

    /** 状态：SETTLED-已结算 / REVERSED-已冲正 */
    private String status;

    /** 操作人 ID（auth userId） */
    private Long operatorId;

    /** 创建时间（即结算时间） */
    private LocalDateTime createTime;
}
