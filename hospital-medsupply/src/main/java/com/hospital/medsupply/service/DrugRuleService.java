package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.DrugRule;
import com.hospital.medsupply.mapper.DrugRuleMapper;
import com.hospital.medsupply.vo.CdssViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CDSS 合理用药规则服务
 * <p>
 * 规则 CRUD + 处方核心校验（剂量上限/重复用药/配伍禁忌/妊娠禁忌），
 * 校验供 {@code /api/medsupply/internal/pharmacy/cdss/check} 内部契约调用，
 * severity=BLOCK 的违规项使校验不通过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugRuleService {

    private static final Set<String> RULE_TYPES = Set.of("MAX_DOSE", "DRUG_DUPLICATE", "DRUG_CONFLICT", "PREGNANCY");
    private static final Set<String> SEVERITIES = Set.of("WARN", "BLOCK");

    private final DrugRuleMapper ruleMapper;

    // ==================== 规则 CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public DrugRule create(DrugRule rule) {
        validateRule(rule);
        ruleMapper.insert(rule);
        log.info("[CDSS] 新增规则: ruleId={}, type={}, drugId={}", rule.getId(), rule.getRuleType(), rule.getDrugId());
        return ruleMapper.selectById(rule.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public DrugRule update(DrugRule rule) {
        if (rule.getId() == null || ruleMapper.selectById(rule.getId()) == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "规则不存在");
        }
        validateRule(rule);
        ruleMapper.update(rule);
        return ruleMapper.selectById(rule.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (ruleMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "规则不存在");
        }
        ruleMapper.delete(id);
    }

    public List<DrugRule> list(String ruleType, Long drugId) {
        return ruleMapper.selectActive(ruleType, drugId);
    }

    private void validateRule(DrugRule rule) {
        if (rule.getRuleType() == null || !RULE_TYPES.contains(rule.getRuleType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "ruleType 仅支持 MAX_DOSE / DRUG_DUPLICATE / DRUG_CONFLICT / PREGNANCY");
        }
        if (rule.getSeverity() != null && !SEVERITIES.contains(rule.getSeverity())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "severity 仅支持 WARN / BLOCK");
        }
        if ("MAX_DOSE".equals(rule.getRuleType()) && rule.getDrugId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "MAX_DOSE 规则必须指定 drugId");
        }
        if ("MAX_DOSE".equals(rule.getRuleType()) && rule.getMaxSingleDose() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "MAX_DOSE 规则必须指定单次剂量上限");
        }
        if ("PREGNANCY".equals(rule.getRuleType()) && rule.getDrugId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "PREGNANCY 规则必须指定 drugId");
        }
    }

    // ==================== 处方核心校验 ====================

    /**
     * 处方合理用药校验
     * <p>
     * 明细项字段（与 clinic 契约一致）：drugId / dosage / quantity / days / drugName。
     *
     * @param patientId 患者（预留，妊娠等人群扩展用）
     * @param items     处方明细列表
     * @return 违规列表（severity=BLOCK 即校验不通过）
     */
    public List<CdssViolationVO> checkPrescription(Long patientId, List<Map<String, Object>> items) {
        List<CdssViolationVO> violations = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return violations;
        }
        List<DrugRule> rules = ruleMapper.selectActive(null, null);
        if (rules.isEmpty()) {
            return violations;
        }
        Set<String> seen = new HashSet<>();

        // ① MAX_DOSE：单次剂量比对（dosage 解析为 mg，解析失败跳过）
        for (Map<String, Object> item : items) {
            Long drugId = toLong(item.get("drugId"));
            if (drugId == null) {
                continue;
            }
            BigDecimal doseMg = parseDosageToMg(item.get("dosage") == null ? null : item.get("dosage").toString());
            if (doseMg == null) {
                continue;
            }
            for (DrugRule rule : rules) {
                if (!"MAX_DOSE".equals(rule.getRuleType()) || !drugId.equals(rule.getDrugId())
                        || rule.getMaxSingleDose() == null) {
                    continue;
                }
                if (doseMg.compareTo(rule.getMaxSingleDose()) > 0) {
                    addViolation(violations, seen, rule, drugId, item.get("drugName"),
                            "单次剂量 " + plain(doseMg) + "mg 超过上限 " + plain(rule.getMaxSingleDose()) + "mg");
                }
            }
        }

        // ② DRUG_DUPLICATE：同处方相同 drug_id 出现 ≥2 次，或命中 paired_drug_id 配对
        Map<Long, Integer> drugCount = new HashMap<>();
        for (Map<String, Object> item : items) {
            Long drugId = toLong(item.get("drugId"));
            if (drugId != null) {
                drugCount.merge(drugId, 1, Integer::sum);
            }
        }
        for (DrugRule rule : rules) {
            if (!"DRUG_DUPLICATE".equals(rule.getRuleType()) || rule.getDrugId() == null) {
                continue;
            }
            Integer count = drugCount.get(rule.getDrugId());
            if (count != null && count >= 2) {
                addViolation(violations, seen, rule, rule.getDrugId(),
                        drugNameOf(items, rule.getDrugId()), "同一处方内重复开具该药品 " + count + " 次");
            } else if (rule.getPairedDrugId() != null
                    && drugCount.containsKey(rule.getDrugId())
                    && drugCount.containsKey(rule.getPairedDrugId())) {
                addViolation(violations, seen, rule, rule.getDrugId(), drugNameOf(items, rule.getDrugId()),
                        "与" + safeName(drugNameOf(items, rule.getPairedDrugId())) + "属重复用药（同类叠加）");
            }
        }

        // ③ DRUG_CONFLICT：明细中任两药存在配伍禁忌规则
        for (int i = 0; i < items.size(); i++) {
            Long drugA = toLong(items.get(i).get("drugId"));
            if (drugA == null) {
                continue;
            }
            for (int j = i + 1; j < items.size(); j++) {
                Long drugB = toLong(items.get(j).get("drugId"));
                if (drugB == null || drugA.equals(drugB)) {
                    continue;
                }
                for (DrugRule rule : rules) {
                    if (!"DRUG_CONFLICT".equals(rule.getRuleType()) || rule.getDrugId() == null) {
                        continue;
                    }
                    boolean paired = (drugA.equals(rule.getDrugId()) && drugB.equals(rule.getPairedDrugId()))
                            || (drugB.equals(rule.getDrugId()) && drugA.equals(rule.getPairedDrugId()));
                    if (paired) {
                        addViolation(violations, seen, rule, drugA, drugNameOf(items, drugA),
                                "与" + safeName(drugNameOf(items, drugB)) + "存在配伍禁忌");
                    }
                }
            }
        }

        // ④ PREGNANCY：规则命中即提示（妊娠状态由前端/病历补充判断）
        for (Map<String, Object> item : items) {
            Long drugId = toLong(item.get("drugId"));
            if (drugId == null) {
                continue;
            }
            for (DrugRule rule : rules) {
                if ("PREGNANCY".equals(rule.getRuleType()) && drugId.equals(rule.getDrugId())) {
                    addViolation(violations, seen, rule, drugId, item.get("drugName"), "妊娠禁忌用药");
                }
            }
        }
        if (!violations.isEmpty()) {
            log.info("[CDSS] 处方校验命中: patientId={}, violations={}", patientId, violations.size());
        }
        return violations;
    }

    private void addViolation(List<CdssViolationVO> violations, Set<String> seen, DrugRule rule,
                              Long drugId, Object drugName, String defaultDesc) {
        String key = rule.getRuleType() + ":" + drugId + ":" + defaultDesc;
        if (!seen.add(key)) {
            return;
        }
        CdssViolationVO vo = new CdssViolationVO();
        vo.setRuleType(rule.getRuleType());
        vo.setSeverity(rule.getSeverity() != null ? rule.getSeverity() : "WARN");
        vo.setDrugId(drugId);
        vo.setDrugName(drugName != null ? drugName.toString() : null);
        vo.setDescription(rule.getDescription() != null && !rule.getDescription().isBlank()
                ? rule.getDescription() : defaultDesc);
        violations.add(vo);
    }

    private static String drugNameOf(List<Map<String, Object>> items, Long drugId) {
        for (Map<String, Object> item : items) {
            if (drugId.equals(toLong(item.get("drugId"))) && item.get("drugName") != null) {
                return item.get("drugName").toString();
            }
        }
        return null;
    }

    private static String safeName(String name) {
        return name != null ? name : "配对药品";
    }

    /**
     * dosage 解析为 mg 数值（如 "0.5g" → 500、"250mg" → 250），解析失败返回 null 跳过校验
     */
    static BigDecimal parseDosageToMg(String dosage) {
        if (dosage == null || dosage.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*(\\d+(?:\\.\\d+)?)").matcher(dosage);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal value = new BigDecimal(matcher.group(1));
        String unit = dosage.substring(matcher.end()).trim().toLowerCase();
        if (unit.startsWith("kg")) {
            return value.multiply(BigDecimal.valueOf(1_000_000));
        }
        if (unit.startsWith("mg")) {
            return value;
        }
        if (unit.startsWith("μg") || unit.startsWith("ug")) {
            return value.divide(BigDecimal.valueOf(1000));
        }
        if (unit.startsWith("g")) {
            return value.multiply(BigDecimal.valueOf(1000));
        }
        // 片/粒/支等无量纲剂量按原值参与比对
        return value;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String s = value.toString().trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
