package com.hospital.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * <p>
 * 声明延迟队列用于订单超时关单。
 * 使用 rabbitmq_delayed_message_exchange 插件实现延迟消息。
 */
@Configuration
public class RabbitMQConfig {

    /** 延迟交换器（需安装 rabbitmq_delayed_message_exchange 插件） */
    public static final String DELAYED_EXCHANGE = "hospital.order.delayed.exchange";

    /** 超时关单队列 */
    public static final String TIMEOUT_QUEUE = "hospital.order.timeout.queue";

    /** 超时关单路由键 */
    public static final String TIMEOUT_ROUTING_KEY = "order.timeout";

    /**
     * 延迟交换器
     * x-delayed-type=direct 表示按路由键精确匹配
     */
    @Bean
    public CustomExchange delayedExchange() {
        return new CustomExchange(DELAYED_EXCHANGE, "x-delayed-message", true, false,
                java.util.Map.of("x-delayed-type", "direct"));
    }

    /**
     * 超时关单队列
     */
    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(TIMEOUT_QUEUE).build();
    }

    /**
     * 绑定：延迟交换器 → 超时队列
     */
    @Bean
    public Binding timeoutBinding(Queue timeoutQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(timeoutQueue).to(delayedExchange).with(TIMEOUT_ROUTING_KEY).noargs();
    }
}
