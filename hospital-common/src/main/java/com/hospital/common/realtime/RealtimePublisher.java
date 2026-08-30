package com.hospital.common.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨服务实时消息发布器（迭代 5 阶段 6）
 * <p>
 * 业务服务（medsupply/payment/patient 等）产生需要实时下推的事件时，
 * 通过本组件发布到 Redis 频道 {@value #CHANNEL}；
 * 拥有 WebSocket/STOMP 的服务（clinic 等）订阅该频道并桥接广播给前端。
 * <p>
 * 链路：业务事件 → RealtimePublisher 发布 Redis → clinic RealtimeBridgeListener
 * 订阅 → SimpMessagingTemplate.convertAndSend(destination, payload)。
 * <p>
 * 通过 {@link ObjectProvider} 懒获取 StringRedisTemplate：未引入 Redis 依赖的服务
 * 不会因缺少 Bean 启动失败，相关方法自动降级为仅日志。
 */
@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RealtimePublisher {

    /** Redis 频道：实时消息桥 */
    public static final String CHANNEL = "hospital:realtime";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RealtimePublisher(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 发布实时消息（Redis 不可用时仅记录日志，不阻断业务）
     *
     * @param destination STOMP 目标（如 /topic/report/{patientId}）
     * @param type        消息类型（业务自定义，如 REPORT_COMPLETED）
     * @param payload     业务数据（会被序列化为 JSON）
     */
    public void publish(String destination, String type, Object payload) {
        try {
            RealtimeMessage message = new RealtimeMessage(destination, type, payload);
            String json = objectMapper.writeValueAsString(message);
            StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.warn("[实时] Redis 不可用，消息丢弃: dest={}, type={}", destination, type);
                return;
            }
            template.convertAndSend(CHANNEL, json);
            log.debug("[实时] 已发布: dest={}, type={}", destination, type);
        } catch (Exception e) {
            log.warn("[实时] 发布失败: dest={}, type={}, err={}", destination, type, e.getMessage());
        }
    }

    /**
     * 便捷：同一患者多个订阅端的消息（如报告完成 / 输液完成）
     */
    public void publishToPatient(Long patientId, String type, Object payload) {
        publish("/topic/report/" + patientId, type, payload);
    }

    /**
     * 实时消息体 JSON 结构
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealtimeMessage {
        /** STOMP 目标 */
        private String destination;
        /** 消息类型 */
        private String type;
        /** 业务数据 */
        private Object payload;
    }
}