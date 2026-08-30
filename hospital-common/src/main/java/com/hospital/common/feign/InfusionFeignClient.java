package com.hospital.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Map;

/**
 * 输液服务 Feign 客户端
 * <p>
 * 供 payment-service 二次缴费成功后回调 medsupply-service，
 * 回写输液单缴费状态（pay_status → PAID）。
 */
@FeignClient(name = "medsupply-service", path = "/api/medsupply/internal/infusion")
public interface InfusionFeignClient {

    /**
     * 输液缴费成功回写
     *
     * @param id 输液单 ID
     * @return 结果 Map
     */
    @PutMapping("/{id}/paid")
    Map<String, Object> markPaid(@PathVariable("id") Long id);

    /**
     * 输液退费回写（payment-service 诊疗费退费后调用，pay_status → REFUNDED）
     *
     * @param id 输液单 ID
     * @return 结果 Map
     */
    @PutMapping("/{id}/refunded")
    Map<String, Object> markRefunded(@PathVariable("id") Long id);
}
