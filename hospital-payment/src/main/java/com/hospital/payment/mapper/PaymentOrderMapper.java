package com.hospital.payment.mapper;

import com.hospital.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** 记录收费员（收费员代缴费成功后写入 cashier_id） */
    int setCashier(@Param("id") Long id, @Param("cashierId") Long cashierId);

    /** 日结汇总：按订单类型 + 支付方式分组统计已支付订单（金额与笔数） */
    List<Map<String, Object>> selectSettleSummary(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /** 日结后打标（将指定收费员当日的已支付未结订单标记为已结） */
    int markSettled(@Param("cashierId") Long cashierId,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);
}
