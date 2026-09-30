package com.hospital.medsupply.controller;

import com.hospital.common.result.Result;
import com.hospital.medsupply.service.AntibioticAuthService;
import com.hospital.medsupply.service.DrugRuleService;
import com.hospital.medsupply.vo.CdssViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药事内部接口（clinic-service 开方联调固定契约，网关白名单放行 /internal，不挂权限注解）
 * <p>
 * 契约一：POST /cdss/check
 *   请求 body：{patientId, items:[{drugId, dosage, quantity, days, drugName}]}
 *   响应：Result&lt;Map&gt;，data = {violations:[{ruleType, severity, drugId, drugName, description}], passed:bool}
 *   severity=BLOCK 的违规项使 passed=false。
 * <p>
 * 契约二：POST /antibiotic/check
 *   请求 body：{doctorId, items:[{drugId}]}
 *   响应：{passed:bool, message:string}
 */
@Slf4j
@RestController
@RequestMapping("/api/medsupply/internal/pharmacy")
@RequiredArgsConstructor
public class PharmacyInternalController {

    private final DrugRuleService drugRuleService;
    private final AntibioticAuthService antibioticAuthService;

    /** CDSS 合理用药校验（clinic 开方时调用） */
    @SuppressWarnings("unchecked")
    @PostMapping("/cdss/check")
    public Result<Map<String, Object>> cdssCheck(@RequestBody Map<String, Object> body) {
        Long patientId = toLong(body.get("patientId"));
        List<Map<String, Object>> items = body.get("items") != null
                ? (List<Map<String, Object>>) body.get("items") : List.of();
        List<CdssViolationVO> violations = drugRuleService.checkPrescription(patientId, items);
        boolean passed = violations.stream().noneMatch(v -> "BLOCK".equals(v.getSeverity()));

        Map<String, Object> data = new HashMap<>();
        data.put("violations", violations);
        data.put("passed", passed);
        return Result.ok(data);
    }

    /** 抗菌药物分级授权校验（clinic 开方时调用） */
    @SuppressWarnings("unchecked")
    @PostMapping("/antibiotic/check")
    public Map<String, Object> antibioticCheck(@RequestBody Map<String, Object> body) {
        Long doctorId = toLong(body.get("doctorId"));
        List<Map<String, Object>> items = body.get("items") != null
                ? (List<Map<String, Object>>) body.get("items") : List.of();
        List<String> violations = antibioticAuthService.checkAntibiotic(doctorId,
                AntibioticAuthService.extractDrugIds(items));
        boolean passed = violations.isEmpty();

        Map<String, Object> result = new HashMap<>();
        result.put("passed", passed);
        result.put("message", passed ? "抗菌药物授权校验通过" : String.join("；", violations));
        return result;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
