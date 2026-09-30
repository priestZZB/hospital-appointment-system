package com.hospital.common.feign.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 药事安全审查 Feign 结果 DTO（medsupply-service 返回）
 * <p>
 * 远端响应为统一响应体 {@code Result} 封装（code/message/data/timestamp），
 * 审查结果位于 {@code data}：{@code {passed, message, violations:[{ruleType, severity, drugName, description}]}}。
 * <p>
 * 使用 {@link #fromMap(Map)} 完成信封解包与强类型转换：
 * <ul>
 *   <li>兼容 Result 信封与裸数据两种形态；</li>
 *   <li>code 非 0、信封缺 data、结构不可识别时返回 {@code null}（调用方按服务不可用 fail-open 处理）。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class PharmacyCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否通过 */
    private Boolean passed;

    /** 提示信息 */
    private String message;

    /** 违规明细（可为空） */
    private List<Violation> violations;

    /**
     * 违规项
     */
    @Data
    @NoArgsConstructor
    public static class Violation implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 规则类型：MAX_DOSE-超剂量 / DRUG_DUPLICATE-重复用药 / DRUG_CONFLICT-药物相互作用 / PREGNANCY-妊娠禁忌 */
        private String ruleType;

        /** 严重级别：BLOCK-拦截 / WARN-警告 */
        private String severity;

        /** 药品名称 */
        private String drugName;

        /** 违规描述 */
        private String description;
    }

    /**
     * 从 Feign 响应构建结果（容忍 Result 信封 / 裸数据两种形态）
     *
     * @param resp Feign 反序列化后的响应 Map
     * @return 审查结果；结构不可识别时返回 null（调用方按服务不可用 fail-open 处理）
     */
    @SuppressWarnings("unchecked")
    public static PharmacyCheckResult fromMap(Map<String, Object> resp) {
        if (resp == null || resp.isEmpty()) {
            return null;
        }
        // 统一响应体：code 非 0 视为远端业务失败，交由调用方 fail-open
        Object code = resp.get("code");
        if (code instanceof Number && ((Number) code).intValue() != 0) {
            return null;
        }
        Map<String, Object> payload = resp;
        Object data = resp.get("data");
        if (data instanceof Map) {
            payload = (Map<String, Object>) data;
        }
        Object passed = payload.get("passed");
        Object message = payload.get("message");
        Object rawViolations = payload.get("violations");
        // 既无 passed 也无 violations/message，视为不可识别结构
        if (!(passed instanceof Boolean) && !(rawViolations instanceof List) && message == null) {
            return null;
        }

        PharmacyCheckResult result = new PharmacyCheckResult();
        result.setPassed(passed instanceof Boolean ? (Boolean) passed : null);
        result.setMessage(message == null ? null : message.toString());
        if (rawViolations instanceof List) {
            List<Violation> violations = new ArrayList<>();
            for (Object raw : (List<Object>) rawViolations) {
                if (!(raw instanceof Map)) {
                    continue;
                }
                Map<String, Object> v = (Map<String, Object>) raw;
                Violation violation = new Violation();
                violation.setRuleType(stringValue(v.get("ruleType")));
                violation.setSeverity(stringValue(v.get("severity")));
                violation.setDrugName(stringValue(v.get("drugName")));
                violation.setDescription(stringValue(v.get("description")));
                violations.add(violation);
            }
            result.setViolations(violations);
        }
        return result;
    }

    /** severity=BLOCK 的违规（开方拦截） */
    public List<Violation> getBlockViolations() {
        return violationsBySeverity("BLOCK");
    }

    /** severity=WARN 的违规（放行并记录） */
    public List<Violation> getWarnViolations() {
        return violationsBySeverity("WARN");
    }

    private List<Violation> violationsBySeverity(String severity) {
        List<Violation> matched = new ArrayList<>();
        if (violations == null) {
            return matched;
        }
        for (Violation v : violations) {
            if (v.getSeverity() != null && v.getSeverity().equalsIgnoreCase(severity)) {
                matched.add(v);
            }
        }
        return matched;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
