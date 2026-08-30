package com.hospital.payment.service;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.ExamFeignClient;
import com.hospital.common.feign.InfusionFeignClient;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.PrescriptionFeignClient;
import com.hospital.common.interceptor.UserContext;
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
 * 二次缴费（诊疗费）服务
 * <p>
 * 负责诊疗费订单的明细计价创建与模拟支付，支付成功后回调 medsupply-service
 * 标记对应业务（输液单等）已缴费。复用 PaymentService 的订单/本地消息/延迟消息模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreatmentPaymentService {

    private final PaymentOrderMapper orderMapper;
    private final LocalMessageMapper messageMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final RabbitTemplate rabbitTemplate;
    private final PatientFeignClient patientFeignClient;
    private final InfusionFeignClient infusionFeignClient;
    private final PrescriptionFeignClient prescriptionFeignClient;
    private final ExamFeignClient examFeignClient;

    /**
     * 创建诊疗费订单（明细计价）
     *
     * @param patientId 患者档案 ID
     * @param orderType 订单类型（DRUG/EXAM/INFUSION/TREATMENT）
     * @param items     明细列表，每项含 itemName、qty、price
     * @param relatedId 关联业务 ID（处方/检查申请/输液单 ID）
     * @return 订单信息 Map
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createTreatmentOrder(Long patientId, String orderType,
                                                    List<Map<String, Object>> items, Long relatedId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "诊疗费明细不能为空");
        }

        // 1. 明细计价：amount = Σ(qty × price)，itemDetail 序列化明细
        BigDecimal amount = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal price = toBigDecimal(item.get("price"));
            BigDecimal qty = toBigDecimal(item.get("qty"));
            amount = amount.add(price.multiply(qty));
        }
        String itemDetail = JSONUtil.toJsonStr(items);

        // 2. 创建订单（appointmentId=null，诊疗费订单）
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(generateOrderNo());
        order.setAppointmentId(null);
        order.setRelatedId(relatedId);
        order.setPatientId(patientId);
        order.setAmount(amount);
        order.setItemDetail(itemDetail);
        order.setOrderType(orderType);
        order.setStatus("PENDING");
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        orderMapper.insert(order);
        log.info("[诊疗缴费] 订单创建: orderId={}, orderNo={}, amount={}, itemDetail={}",
                order.getId(), order.getOrderNo(), amount, itemDetail);

        // 3. 同一事务插入本地消息表
        LocalMessage msg = new LocalMessage();
        msg.setMessageId(UUID.fastUUID().toString());
        msg.setExchange("hospital.order.exchange");
        msg.setRoutingKey("order.create");
        msg.setMessageBody(JSONUtil.toJsonStr(order));
        msg.setStatus("PENDING");
        msg.setRetryCount(0);
        msg.setMaxRetry(3);
        msg.setBusinessType("TREATMENT_ORDER_CREATE");
        msg.setBusinessId(order.getOrderNo());
        messageMapper.insert(msg);
        log.info("[诊疗缴费] 本地消息表已插入: messageId={}", msg.getMessageId());

        // 4. 发送 30 分钟延迟消息（超时关单）
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DELAYED_EXCHANGE,
                    RabbitMQConfig.TIMEOUT_ROUTING_KEY,
                    order.getOrderNo(),
                    message -> {
                        message.getMessageProperties().setHeader("x-delay", 30 * 60 * 1000);
                        return message;
                    }
            );
            log.info("[诊疗缴费] 延迟消息已发送: orderNo={}, delay=30min", order.getOrderNo());
        } catch (Exception e) {
            log.error("[诊疗缴费] 延迟消息发送失败（将由XXL-JOB兜底）: orderNo={}", order.getOrderNo(), e);
        }

        return Map.of(
                "id", order.getId(),
                "orderNo", order.getOrderNo(),
                "patientId", order.getPatientId(),
                "amount", order.getAmount(),
                "orderType", order.getOrderType(),
                "status", order.getStatus(),
                "expireTime", order.getExpireTime().toString()
        );
    }

    /**
     * 支付诊疗费（患者自付，模拟支付）
     * <p>
     * 支付成功后按订单类型回调对应服务标记业务单据已缴费（Feign）：
     * DRUG→clinic 处方、EXAM→medsupply 检查申请、INFUSION→medsupply 输液单。
     *
     * @param orderId 订单 ID
     * @param userId  当前登录用户 ID（auth userId）
     * @return 支付结果 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderVO payTreatmentOrder(Long orderId, Long userId) {
        return doPayTreatmentOrder(orderId, userId, null);
    }

    /**
     * 支付诊疗费（收费员代缴费，模拟支付）
     * <p>
     * 与患者自付逻辑相同，但跳过归属校验（收费员可代任意患者收费），
     * 支付成功后额外把收费员 ID 写入订单 cashier_id。
     *
     * @param orderId   订单 ID
     * @param userId    当前登录用户 ID（auth userId，收费员本人）
     * @param cashierId 收费员 ID（auth userId，记录于订单）
     * @return 支付结果 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderVO payTreatmentOrder(Long orderId, Long userId, Long cashierId) {
        return doPayTreatmentOrder(orderId, null, cashierId);
    }

    /**
     * 诊疗费支付核心逻辑
     *
     * @param orderId     订单 ID
     * @param ownerUserId 需校验归属的用户 ID（患者自付时传；收费员代缴时传 null 跳过）
     * @param cashierId   收费员 ID（收费员代缴时传；患者自付时传 null）
     */
    private PaymentOrderVO doPayTreatmentOrder(Long orderId, Long ownerUserId, Long cashierId) {
        PaymentOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }
        if (ownerUserId != null) {
            checkOrderOwner(order, ownerUserId);
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

        // 收费员代缴费：记录收费员
        if (cashierId != null) {
            orderMapper.setCashier(orderId, cashierId);
            log.info("[诊疗缴费] 收费员代缴费: orderId={}, cashierId={}", orderId, cashierId);
        }

        // Feign 回调对应服务标记业务单据已缴费（三类）
        try {
            if (order.getRelatedId() != null) {
                switch (order.getOrderType() == null ? "" : order.getOrderType()) {
                    case "INFUSION" -> {
                        infusionFeignClient.markPaid(order.getRelatedId());
                        log.info("[诊疗缴费] 已回调 medsupply 标记输液单已缴费: infusionOrderId={}", order.getRelatedId());
                    }
                    case "DRUG" -> {
                        prescriptionFeignClient.markPaid(order.getRelatedId(), order.getAmount());
                        log.info("[诊疗缴费] 已回调 clinic 标记处方已缴费: prescriptionId={}, amount={}",
                                order.getRelatedId(), order.getAmount());
                    }
                    case "EXAM" -> {
                        examFeignClient.markPaid(order.getRelatedId(), order.getAmount());
                        log.info("[诊疗缴费] 已回调 medsupply 标记检查申请已缴费: examId={}, amount={}",
                                order.getRelatedId(), order.getAmount());
                    }
                    default -> log.warn("[诊疗缴费] 订单类型 {} 无需回写业务单据: orderId={}",
                            order.getOrderType(), orderId);
                }
            }
        } catch (Exception e) {
            log.error("[诊疗缴费] 回调业务服务标记已缴费失败: orderId={}, orderType={}, relatedId={}",
                    orderId, order.getOrderType(), order.getRelatedId(), e);
            throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR);
        }

        log.info("[诊疗缴费] 支付成功: orderId={}, orderNo={}", orderId, order.getOrderNo());
        return toVO(orderMapper.selectById(orderId));
    }

    /**
     * 诊疗费退费（收费员/管理员操作）
     * <p>
     * 校验订单已支付后乐观锁置为 REFUNDED，写退款记录，并尽力回写业务单据
     * pay_status=REFUNDED（DRUG→处方、EXAM→检查申请、INFUSION→输液单）。
     * 业务侧回写失败仅记 warn，不退费以 payment 侧为准（尽力同步）。
     *
     * @param orderId      订单 ID
     * @param refundReason 退费原因
     * @param operatorId   操作人 ID（收费员/管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundTreatmentOrder(Long orderId, String refundReason, Long operatorId) {
        PaymentOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.REFUND_NOT_ALLOWED, "仅已支付订单可退费");
        }
        if ("REGISTRATION".equals(order.getOrderType())) {
            throw new BusinessException(ErrorCodeEnum.REFUND_NOT_ALLOWED, "挂号费订单请通过预约退款流程处理");
        }

        // 乐观锁置为已退款
        int rows = orderMapper.updateStatusWithVersion(order.getId(), "REFUNDED", order.getVersion());
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.REFUND_NOT_ALLOWED, "订单状态已变更，请刷新后重试");
        }

        // 写退款记录（诊疗费退费不关联预约）
        RefundRecord record = new RefundRecord();
        record.setRefundNo(generateRefundNo());
        record.setPaymentOrderId(order.getId());
        record.setAppointmentId(null);
        record.setRefundAmount(order.getAmount());
        record.setRefundReason(refundReason);
        record.setRefundType("CASHIER_REFUND");
        record.setOrderType(order.getOrderType());
        record.setStatus("COMPLETED");
        refundRecordMapper.insert(record);
        log.info("[诊疗退费] 退款记录已写入: orderId={}, refundNo={}, amount={}",
                order.getId(), record.getRefundNo(), order.getAmount());

        // 尽力回写业务单据 pay_status=REFUNDED（失败不阻断、不回滚）
        try {
            if (order.getRelatedId() != null) {
                switch (order.getOrderType() == null ? "" : order.getOrderType()) {
                    case "DRUG" -> prescriptionFeignClient.markRefunded(order.getRelatedId());
                    case "EXAM" -> examFeignClient.markRefunded(order.getRelatedId());
                    case "INFUSION" -> infusionFeignClient.markRefunded(order.getRelatedId());
                    default -> log.warn("[诊疗退费] 订单类型 {} 无需回写业务单据: orderId={}",
                            order.getOrderType(), orderId);
                }
            }
        } catch (Exception e) {
            log.warn("[诊疗退费] 回写业务单据 REFUNDED 失败（以 payment 侧为准，尽力同步）: orderId={}, orderType={}, relatedId={}",
                    orderId, order.getOrderType(), order.getRelatedId(), e);
        }

        log.info("[诊疗退费] 退费完成: orderId={}, operatorId={}, amount={}",
                orderId, operatorId, order.getAmount());
    }

    /**
     * 通过 userId 解析患者档案 ID（供 Controller 创建诊疗费订单前解析）
     */
    public Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                return null;
            }
            return toLong(patientInfo.get("id"));
        } catch (Exception e) {
            log.warn("[诊疗缴费] 查询患者信息失败: userId={}", userId, e);
            return null;
        }
    }

    // ==================== 私有方法 ====================

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = UUID.fastUUID().toString().substring(0, 8).toUpperCase();
        return "PAY" + date + random;
    }

    private String generateRefundNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = UUID.fastUUID().toString().substring(0, 8).toUpperCase();
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

    /**
     * 订单归属校验：管理员可操作任意订单，患者仅可操作本人订单
     */
    private void checkOrderOwner(PaymentOrder order, Long userId) {
        if (UserContext.isAdminOrSuperAdmin()) {
            return;
        }
        Long patientId = resolvePatientId(userId);
        if (patientId == null || !patientId.equals(order.getPatientId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权操作他人订单");
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
