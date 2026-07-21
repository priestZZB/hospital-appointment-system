package com.hospital.payment.mapper;

import com.hospital.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付订单表 Mapper
 */
@Mapper
public interface PaymentOrderMapper {

    /** 插入 */
    int insert(PaymentOrder order);

    /** 根据主键查询 */
    PaymentOrder selectById(@Param("id") Long id);

    /** 根据订单号查询 */
    PaymentOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 根据预约ID查询 */
    PaymentOrder selectByAppointmentId(@Param("appointmentId") Long appointmentId);

    /** 更新订单状态（乐观锁） */
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("status") String status,
                                @Param("version") Integer version);

    /** 模拟支付（更新支付状态+时间+方式） */
    int markAsPaid(@Param("id") Long id,
                   @Param("payMethod") String payMethod,
                   @Param("version") Integer version);

    /** 查询超时未支付订单 */
    List<PaymentOrder> selectTimeoutOrders(@Param("status") String status,
                                           @Param("now") LocalDateTime now);
}
