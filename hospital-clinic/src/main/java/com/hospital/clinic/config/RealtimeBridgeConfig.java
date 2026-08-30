package com.hospital.clinic.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.realtime.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 跨服务实时消息桥接（迭代 5 阶段 6）
 * <p>
 * 订阅 Redis 频道 {@link RealtimePublisher#CHANNEL}，收到其他服务发布的实时消息后，
 * 通过本地 STOMP {@link SimpMessagingTemplate} 桥接推送给前端订阅者。
 * 跨服务链路：medsupply/payment 等 → RealtimePublisher(Redis) → 本监听器 → STOMP 广播。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RealtimeBridgeConfig {

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (MessageListener) (message, pattern) -> handleRealtimeMessage(message),
                new ChannelTopic(RealtimePublisher.CHANNEL));
        return container;
    }

    /**
     * 处理 Redis 实时消息并桥接 STOMP
     */
    private void handleRealtimeMessage(Message message) {
        try {
            String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);
            String destination = node.path("destination").asText();
            String type = node.path("type").asText();
            Object payload = objectMapper.treeToValue(node.get("payload"), Object.class);
            if (destination.isBlank()) {
                return;
            }
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("[实时桥接] destination={}, type={}", destination, type);
        } catch (Exception e) {
            log.warn("[实时桥接] 消息处理失败: {}", e.getMessage());
        }
    }
}