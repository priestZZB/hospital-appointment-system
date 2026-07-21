package com.hospital.payment.mapper;

import com.hospital.payment.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 退款记录表 Mapper
 */
@Mapper
public interface RefundRecordMapper {

    /** 插入 */
    int insert(RefundRecord record);

    /** 根据主键查询 */
    RefundRecord selectById(@Param("id") Long id);

    /** 根据支付订单ID查询 */
    RefundRecord selectByPaymentOrderId(@Param("paymentOrderId") Long paymentOrderId);
}
