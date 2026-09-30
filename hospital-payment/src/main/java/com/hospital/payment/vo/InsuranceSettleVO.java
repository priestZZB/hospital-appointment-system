package com.hospital.payment.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 医保结算单 VO（迭代11 H2）
 * <p>
 * 在结算单实体基础上，把 detail_json 反序列化为逐项明细数组返回。
 */
@Data
@Builder
public class InsuranceSettleVO {

    private Long id;

    /** 结算单号（票据号同此号） */
    private String settleNo;

    private Long patientId;

    /** 医保号 */
    private String insuranceNo;

    /** 业务类型：REGISTER/OUTPATIENT/INPATIENT */
    private String bizType;

    /** 关联业务 ID */
    private Long bizRefId;

    /** 总金额 */
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

    /** 状态：SETTLED/REVERSED */
    private String status;

    /** 操作人 ID */
    private Long operatorId;

    /** 结算时间 */
    private LocalDateTime createTime;

    /** 逐项明细（itemName/itemType/catalogClass/amount/insurancePay/personalPay/firstSelfPay/note） */
    private List<Map<String, Object>> detail;
}
