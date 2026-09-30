package com.hospital.common.feign;

import com.hospital.common.feign.dto.PharmacyCheckDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 药事服务 Feign 客户端
 * <p>
 * 供 clinic-service 开方时调用 medsupply-service 的药事安全审查内部接口：
 * <ul>
 *   <li>CDSS 处方安全审查（超剂量/重复用药/药物相互作用/妊娠禁忌，B9）</li>
 *   <li>抗菌药物处方授权校验（B10）</li>
 * </ul>
 * <p>
 * 远端响应为统一响应体 Result 封装，审查结果位于 data，
 * 调用方通过 {@link com.hospital.common.feign.dto.PharmacyCheckResult#fromMap(Map)} 解包。
 */
@FeignClient(name = "medsupply-service", path = "/api/medsupply/internal/pharmacy")
public interface PharmacyFeignClient {

    /**
     * CDSS 处方安全审查
     *
     * @param dto 患者 + 处方明细药品（drugId/drugName/dosage/quantity/days）
     * @return Result.data = {"passed":true/false,"violations":[{ruleType,severity,drugName,description}]}
     */
    @PostMapping("/cdss/check")
    Map<String, Object> cdssCheck(@RequestBody PharmacyCheckDTO dto);

    /**
     * 抗菌药物处方授权校验
     *
     * @param dto 医生 + 处方明细药品（drugId）
     * @return Result.data = {"passed":true/false,"message":"..."}
     */
    @PostMapping("/antibiotic/check")
    Map<String, Object> antibioticCheck(@RequestBody PharmacyCheckDTO dto);
}
