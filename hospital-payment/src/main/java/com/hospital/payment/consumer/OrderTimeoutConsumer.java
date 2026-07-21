package com.hospital.payment.consumer;

import com.hospital.payment.config.RabbitMQConfig;
import com.hospital.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单消费者
 * <p>
 * 监听延迟队列，30 分钟后触发关单。
 * 使用乐观锁保证幂等：同一订单只有关单一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final PaymentService paymentService;

    @RabbitListener(queues = RabbitMQConfig.TIMEOUT_QUEUE)
    public void onMessage(String orderNo) {
        log.info("[MQ] 收到超时关单消息: orderNo={}", orderNo);
        try {
            paymentService.closeTimeoutOrder(orderNo);
        } catch (Exception e) {
            log.error("[MQ] 超时关单失败: orderNo={}", orderNo, e);
        }
    }
}
