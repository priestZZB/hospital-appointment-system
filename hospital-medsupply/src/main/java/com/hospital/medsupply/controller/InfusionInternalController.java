package com.hospital.medsupply.controller;

import com.hospital.medsupply.mapper.InfusionOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 输液内部接口（供 payment-service 通过 Feign 回调输液缴费结果）
 * <p>
 * 对应支付成功后回写输液单缴费状态为 PAID。
 */
@Slf4j
@RestController
@RequestMapping("/api/medsupply/internal/infusion")
@RequiredArgsConstructor
public class InfusionInternalController {

    private final InfusionOrderMapper infusionOrderMapper;

    /** 输液缴费成功回写 pay_status=PAID */
    @PutMapping("/{id}/paid")
    public Map<String, Object> markPaid(@PathVariable("id") Long id) {
        int rows = infusionOrderMapper.updatePayStatus(id, "PAID");
        log.info("[输液] 缴费回写: infusionOrderId={}, rows={}", id, rows);
        return Map.of("id", id, "payStatus", "PAID", "success", rows > 0);
    }

    /** 输液退费回写 pay_status=REFUNDED */
    @PutMapping("/{id}/refunded")
    public Map<String, Object> markRefunded(@PathVariable("id") Long id) {
        int rows = infusionOrderMapper.updatePayStatus(id, "REFUNDED");
        log.info("[输液] 退费回写: infusionOrderId={}, rows={}", id, rows);
        return Map.of("id", id, "payStatus", "REFUNDED", "success", rows > 0);
    }
}
