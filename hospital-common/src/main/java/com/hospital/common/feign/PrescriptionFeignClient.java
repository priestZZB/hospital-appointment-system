package com.hospital.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 处方 Feign 客户端
 * <p>
 * 供 medsupply-service 在处方审核/发药确认时读取 clinic-service 的处方
 * 明细并同步处方状态；供 payment-service 二次缴费成功后回写处方缴费状态。
 */
@FeignClient(name = "clinic-service", path = "/api/clinic/internal")
public interface PrescriptionFeignClient {

    /**
     * 查询处方明细（内部接口）
     *
     * @param prescriptionId 处方 ID
     * @return 处方明细列表（drugId/drugName/specification/quantity/unit 等）
     */
    @GetMapping("/prescription/{id}/items")
    List<Map<String, Object>> getItems(@PathVariable("id") Long prescriptionId);

    /**
     * 更新处方状态（内部接口）
     *
     * @param prescriptionId 处方 ID
     * @param status         目标状态（REVIEW_PASSED/REVIEW_REJECTED/DISPENSED）
     * @param expectedStatus 期望当前状态（状态机校验，可为空）
     * @return 更新结果
     */
    @PostMapping("/prescription/{id}/status")
    Map<String, Object> updateStatus(@PathVariable("id") Long prescriptionId,
                                     @RequestParam("status") String status,
                                     @RequestParam(value = "expectedStatus", required = false) String expectedStatus,
                                     @RequestParam(value = "reviewComment", required = false) String reviewComment);

    /**
     * 处方缴费成功回写（payment-service 二次缴费成功后调用）
     *
     * @param prescriptionId 处方 ID
     * @param amount         实收金额
     * @return 结果 Map
     */
    @PutMapping("/prescription/{id}/paid")
    Map<String, Object> markPaid(@PathVariable("id") Long prescriptionId,
                                 @RequestParam("amount") BigDecimal amount);

    /**
     * 查询处方缴费状态（medsupply-service 发药前校验是否已缴费）
     *
     * @param prescriptionId 处方 ID
     * @return Map（payStatus 字段）
     */
    @GetMapping("/prescription/{id}/pay-status")
    Map<String, Object> getPayStatus(@PathVariable("id") Long prescriptionId);

    /**
     * 处方退费回写（payment-service 诊疗费退费后调用，pay_status → REFUNDED）
     *
     * @param prescriptionId 处方 ID
     * @return 结果 Map
     */
    @PutMapping("/prescription/{id}/refunded")
    Map<String, Object> markRefunded(@PathVariable("id") Long prescriptionId);
}
