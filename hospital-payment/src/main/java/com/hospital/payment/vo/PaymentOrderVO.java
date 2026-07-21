package com.hospital.payment.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单 VO
 */
@Data
@Builder
public class PaymentOrderVO {

    private Long id;
    private String orderNo;
    private Long appointmentId;
    private Long patientId;
    private BigDecimal amount;
    private String orderType;
    private String status;
    private LocalDateTime payTime;
    private String payMethod;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
