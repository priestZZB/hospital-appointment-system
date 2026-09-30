package com.hospital.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 医保结算请求 DTO（迭代11 H2）
 * <p>
 * body 示例：
 * <pre>{@code
 * {
 *   "patientId": 1,
 *   "insuranceNo": "MI12345678",
 *   "bizType": "OUTPATIENT",
 *   "bizRefId": 1001,
 *   "operatorId": 2,
 *   "items": [
 *     {"itemType": "CHARGED_ITEM", "refId": 1, "itemName": "普通诊查费", "amount": 100},
 *     {"itemType": "DRUG", "refId": 5, "itemName": "阿莫西林胶囊", "amount": 100},
 *     {"itemType": "MATERIAL", "refId": 9, "itemName": "进口敷料", "amount": 100}
 *   ]
 * }
 * }</pre>
 */
@Data
public class InsuranceSettleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 患者档案 ID（必填） */
    private Long patientId;

    /** 医保号（可空，模拟场景直接落库） */
    private String insuranceNo;

    /** 业务类型：REGISTER/OUTPATIENT/INPATIENT（必填） */
    private String bizType;

    /** 关联业务 ID（既有支付订单/结算单 id，可空） */
    private Long bizRefId;

    /** 操作人 ID（可空，默认取当前登录用户） */
    private Long operatorId;

    /** 收费明细（必填，至少 1 项） */
    private List<SettleItemDTO> items;

    /**
     * 逐项明细
     */
    @Data
    public static class SettleItemDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 业务类型：REGISTER/DRUG/EXAM/LAB/TREATMENT/MATERIAL/CHARGED_ITEM */
        private String itemType;

        /** 引用业务 ID（用于医保目录解析，可空——为空按未配置自费处理） */
        private Long refId;

        /** 项目名称（用于票据与明细，可空——为空回退目录名） */
        private String itemName;

        /** 金额（必填，>= 0） */
        private BigDecimal amount;
    }
}
