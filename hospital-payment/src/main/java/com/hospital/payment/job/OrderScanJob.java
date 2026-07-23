package com.hospital.payment.job;

import com.hospital.payment.service.PaymentService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB 定时任务：扫表兜底关单
 * <p>
 * 每 5 分钟扫描 payment_order 表中超时未支付订单，触发关单。
 * 作为 RabbitMQ 延迟消息的兜底方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScanJob {

    private final PaymentService paymentService;

    @XxlJob("scanTimeoutOrders")
    public void execute() {
        log.info("[XXL-JOB] 开始扫描超时订单");
        try {
            paymentService.scanTimeoutOrders();
            log.info("[XXL-JOB] 超时订单扫描完成");
        } catch (Exception e) {
            log.error("[XXL-JOB] 超时订单扫描异常", e);
        }
    }
}
