package com.hospital.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel 网关限流配置
 * <p>
 * 配置 Sentinel Gateway 过滤器 + 限流降级响应。
 * 当请求被 Sentinel 限流时，返回 Result 格式的 JSON 错误信息。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SentinelGatewayConfig {

    private final ObjectMapper objectMapper;

    /**
     * 注入 Sentinel Gateway 过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler();
    }

    /**
     * 初始化 Sentinel Gateway 限流降级回调
     * <p>
     * 限流触发时返回统一格式的 JSON 响应（匹配 Result 结构）
     */
    @PostConstruct
    public void initBlockHandlers() {
        BlockRequestHandler blockRequestHandler = (serverWebExchange, throwable) -> {
            Result<Void> result = Result.fail(ErrorCodeEnum.RATE_LIMIT_EXCEEDED);

            byte[] bytes;
            try {
                bytes = objectMapper.writeValueAsBytes(result);
            } catch (Exception e) {
                log.error("[Sentinel] 序列化限流响应失败", e);
                bytes = "{\"code\":9999,\"message\":\"系统繁忙\"}".getBytes(StandardCharsets.UTF_8);
            }

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new String(bytes, StandardCharsets.UTF_8));
        };

        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
        log.info("[Sentinel] Gateway 限流降级回调已注册");
    }
}
