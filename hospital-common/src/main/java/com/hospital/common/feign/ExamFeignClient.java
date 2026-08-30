package com.hospital.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 检查申请 Feign 客户端
 * <p>
 * 供 payment-service 二次缴费成功后回调 medsupply-service，
 * 回写检查申请缴费状态（pay_status → PAID）并记录实收金额。
 */
@FeignClient(name = "medsupply-service", path = "/api/medsupply/internal/exam")
public interface ExamFeignClient {

    /**
     * 检查缴费成功回写
     *
     * @param id     检查申请 ID
     * @param amount 实收金额
     * @return 结果 Map
     */
    @PutMapping("/{id}/paid")
    Map<String, Object> markPaid(@PathVariable("id") Long id,
                                 @RequestParam("amount") BigDecimal amount);

    /**
     * 检查退费回写（payment-service 诊疗费退费后调用，pay_status → REFUNDED）
     *
     * @param id 检查申请 ID
     * @return 结果 Map
     */
    @PutMapping("/{id}/refunded")
    Map<String, Object> markRefunded(@PathVariable("id") Long id);
}
