package com.hospital.payment.service;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.AppointmentFeignClient;
import com.hospital.payment.config.RabbitMQConfig;
import com.hospital.payment.entity.LocalMessage;
import com.hospital.payment.entity.PaymentOrder;
import com.hospital.payment.entity.RefundRecord;
import com.hospital.payment.mapper.LocalMessageMapper;
import com.hospital.payment.mapper.PaymentOrderMapper;
import com.hospital.payment.mapper.RefundRecordMapper;
import com.hospital.payment.vo.PaymentOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 支付订单服务
 * <p>
 * 负责订单创建、模拟支付、退款、超时关单（含扫表兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderMapper orderMapper;
    private final LocalMessageMapper messageMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final AppointmentFeignClient appointmentFeignClient;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 创建支付订单（内部调用，同一事务：insert order + insert local_message）
     *
     * @param dto 订单参数: appointmentId, patientId, amount, orderType
     * @return 订单信息 Map
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Map<String, Object> dto) {
        Long appointmentId = toLong(dto.get("appointmentId"));
        Long patientId = toLong(dto.get("patientId"));
        BigDecimal amount = toBigDecimal(dto.get("amount"));
        String orderType = (String) dto.getOrDefault("orderType", "REGISTRATION");

        // 1. 创建订单
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(generateOrderNo());
        order.setAppointmentId(appointmentId);
        order.setPatientId(patientId);
        order.setAmount(amount);
        order.setOrderType(orderType);
        order.setStatus("PENDING");
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        orderMapper.insert(order);
        log.info("[支付] 订单创建: orderId={}, orderNo={}, amount={}", order.getId(), order.getOrderNo(), amount);

        // 2. 同一事务插入本地消息表
        LocalMessage msg = new LocalMessage();
        msg.setMessageId(UUID.fastUUID().toString());
        msg.setExchange("hospital.order.exchange");
        msg.setRoutingKey("order.create");
        msg.setMessageBody(JSONUtil.toJsonStr(order));
        msg.setStatus("PENDING");
        msg.setRetryCount(0);
        msg.setMaxRetry(3);
        msg.setBusinessType("ORDER_CREATE");
        msg.setBusinessId(order.getOrderNo());
        messageMapper.insert(msg);
        log.info("[支付] 本地消息表已插入: messageId={}", msg.getMessageId());

        // 3. 发送 30 分钟延迟消息（超时关单）
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DELAYED_EXCHANGE,
                    RabbitMQConfig.TIMEOUT_ROUTING_KEY,
                    order.getOrderNo(),
                    message -> {
                        message.getMessageProperties().setHeader("x-delay", 30 * 60 * 1000); // 30分钟
                        return message;
                    }
            );
            log.info("[支付] 延迟消息已发送: orderNo={}, delay=30min", order.getOrderNo());
        } catch (Exception e) {
            log.error("[支付] 延迟消息发送失败（将由XXL-JOB兜底）: orderNo={}", order.getOrderNo(), e);
        }

        return Map.of(
                "id", order.getId(),
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "expireTime", order.getExpireTime().toString()
        );
    }

    /**
     * 模拟支付
     *
     * @param orderId 订单 ID
     * @return 支付结果 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderVO processPayment(Long orderId) {
        PaymentOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        if ("PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_ALREADY_PAID);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_EXPIRED);
        }

        // 乐观锁更新为已支付
        int rows = orderMapper.markAsPaid(orderId, "SIMULATED", order.getVersion());
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.ORDER_EXPIRED);
        }

        // Feign 回调 clinic-service 确认号源锁定
        try {
            appointmentFeignClient.confirmLock(order.getAppointmentId());
            log.info("[支付] 已回调 clinic-service 确认锁定: appointmentId={}", order.getAppointmentId());
        } catch (Exception e) {
            log.error("[支付] 回调 clinic 确认锁定失败: appointmentId={}", order.getAppointmentId(), e);
        }

        // 发送挂号成功通知
        notificationService.createNotification(order.getPatientId(),
                "预约成功", "您已成功预约挂号，订单号：" + order.getOrderNo(),
                "APPOINTMENT", order.getOrderNo());

        log.info("[支付] 支付成功: orderId={}, orderNo={}", orderId, order.getOrderNo());
        return toVO(orderMapper.selectById(orderId));
    }

    /**
     * 退款
     *
     * @param dto 退款参数: appointmentId, refundReason, refundType
     */
    @Transactional(rollbackFor = Exception.class)
    public void refund(Map<String, Object> dto) {
        Long appointmentId = toLong(dto.get("appointmentId"));
        String refundReason = (String) dto.getOrDefault("refundReason", "退款");
        String refundType = (String) dto.getOrDefault("refundType", "PATIENT_CANCEL");

        PaymentOrder order = orderMapper.selectByAppointmentId(appointmentId);
        if (order == null) {
            log.warn("[退款] 未找到对应订单: appointmentId={}", appointmentId);
            return;
        }
        if (!"PAID".equals(order.getStatus())) {
            log.warn("[退款] 订单非已支付状态，跳过退款: orderId={}, status={}", order.getId(), order.getStatus());
            return;
        }

        // 创建退款记录
        RefundRecord record = new RefundRecord();
        record.setRefundNo(generateRefundNo());
        record.setPaymentOrderId(order.getId());
        record.setAppointmentId(appointmentId);
        record.setRefundAmount(order.getAmount());
        record.setRefundReason(refundReason);
        record.setRefundType(refundType);
        record.setStatus("COMPLETED");
        refundRecordMapper.insert(record);

        // 更新订单为已退款
        orderMapper.updateStatusWithVersion(order.getId(), "REFUNDED", order.getVersion());

        // Feign 回调 clinic 释放号源
        try {
            appointmentFeignClient.releaseSlot(appointmentId);
            log.info("[退款] 已回调 clinic 释放号源: appointmentId={}", appointmentId);
        } catch (Exception e) {
            log.error("[退款] 回调 clinic 释放号源失败: appointmentId={}", appointmentId, e);
        }

        // 发送退款通知
        notificationService.createNotification(order.getPatientId(),
                "退款通知", "您的预约已退款，金额：" + order.getAmount() + "元，原因：" + refundReason,
                "CANCEL", order.getOrderNo());

        log.info("[退款] 退款完成: orderId={}, refundNo={}, amount={}", order.getId(), record.getRefundNo(), order.getAmount());
    }

    /**
     * 超时关单（乐观锁幂等）
     *
     * @param orderNo 订单编号
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrder(String orderNo) {
        PaymentOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return;
        }
        if (!"PENDING".equals(order.getStatus())) {
            return; // 幂等：已支付/已取消/已超时则跳过
        }

        // 乐观锁关单
        int rows = orderMapper.updateStatusWithVersion(order.getId(), "TIMEOUT", order.getVersion());
        if (rows == 0) {
            return; // 已被其他路径关单
        }

        // Feign 调用 clinic-service 释放号源
        try {
            appointmentFeignClient.releaseSlot(order.getAppointmentId());
            log.info("[关单] 已释放号源: appointmentId={}", order.getAppointmentId());
        } catch (Exception e) {
            log.error("[关单] 释放号源失败: appointmentId={}", order.getAppointmentId(), e);
        }

        // 发送超时通知
        notificationService.createNotification(order.getPatientId(),
                "订单超时", "您的订单已超时关闭，订单号：" + orderNo,
                "SYSTEM", orderNo);

        log.info("[关单] 超时关单完成: orderNo={}", orderNo);
    }

    /**
     * XXL-JOB 扫表兜底：扫描超时未支付订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void scanTimeoutOrders() {
        List<PaymentOrder> timeoutOrders = orderMapper.selectTimeoutOrders(
                "PENDING", LocalDateTime.now());
        log.info("[扫表] 发现超时订单 {} 个", timeoutOrders.size());
        for (PaymentOrder order : timeoutOrders) {
            try {
                closeTimeoutOrder(order.getOrderNo());
            } catch (Exception e) {
                log.error("[扫表] 关单失败: orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 查询订单状态
     */
    public PaymentOrderVO getStatus(Long orderId) {
        PaymentOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }
        return toVO(order);
    }

    // ==================== 私有方法 ====================

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.fastUUID().toString().substring(0, 6).toUpperCase();
        return "PAY" + date + random;
    }

    private String generateRefundNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.fastUUID().toString().substring(0, 6).toUpperCase();
        return "REF" + date + random;
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private PaymentOrderVO toVO(PaymentOrder o) {
        return PaymentOrderVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .appointmentId(o.getAppointmentId())
                .patientId(o.getPatientId())
                .amount(o.getAmount())
                .orderType(o.getOrderType())
                .status(o.getStatus())
                .payTime(o.getPayTime())
                .payMethod(o.getPayMethod())
                .expireTime(o.getExpireTime())
                .createTime(o.getCreateTime())
                .build();
    }
}
