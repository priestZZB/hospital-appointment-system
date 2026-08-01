package com.hospital.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 降级关键词匹配服务
 * <p>
 * 当 deepseek 调用超时或异常时，使用 16 组预置的「症状关键词 → 科室」映射表
 * 进行关键词分词 + 交集最大匹配，返回最佳科室推荐。
 */
@Slf4j
@Service
public class FallbackService {

    /**
     * 降级匹配结果
     */
    public record FallbackResult(String deptName, BigDecimal confidence) {
    }

    /**
     * 症状关键词 → 科室映射表（16 组）
     * <p>
     * 每组包含：关键词集合、目标科室、权重
     */
    private static final List<SymptomRule> RULES = new ArrayList<>();

    static {
        RULES.add(new SymptomRule(Set.of("发烧", "咳嗽", "喉咙痛"), "呼吸内科", 10));
        RULES.add(new SymptomRule(Set.of("发烧", "咳嗽", "呼吸困难"), "呼吸内科", 10));
        RULES.add(new SymptomRule(Set.of("胸痛", "心悸", "气短"), "心内科", 10));
        RULES.add(new SymptomRule(Set.of("头痛", "头晕", "恶心"), "神经内科", 8));
        RULES.add(new SymptomRule(Set.of("腹痛", "腹泻", "呕吐"), "消化内科", 8));
        RULES.add(new SymptomRule(Set.of("皮疹", "瘙痒"), "皮肤科", 7));
        RULES.add(new SymptomRule(Set.of("骨折", "扭伤", "关节痛"), "骨科", 10));
        RULES.add(new SymptomRule(Set.of("视力下降", "眼红", "眼痛"), "眼科", 9));
        RULES.add(new SymptomRule(Set.of("听力下降", "耳鸣", "耳痛"), "耳鼻喉科", 9));
        RULES.add(new SymptomRule(Set.of("牙痛", "牙龈出血"), "口腔科", 8));
        RULES.add(new SymptomRule(Set.of("儿童", "发烧"), "儿科", 9));
        RULES.add(new SymptomRule(Set.of("月经不调", "痛经"), "妇产科", 9));
        RULES.add(new SymptomRule(Set.of("尿频", "尿急", "尿痛"), "内科", 7));
        RULES.add(new SymptomRule(Set.of("鼻塞", "流涕", "打喷嚏"), "耳鼻喉科", 8));
        RULES.add(new SymptomRule(Set.of("乏力", "消瘦", "多饮", "多尿"), "内分泌科", 9));
        RULES.add(new SymptomRule(Set.of("腰痛", "下肢麻木"), "骨科", 7));
    }

    /**
     * 关键词匹配（交集最大匹配法）
     * <p>
     * 将症状文本分词后，与每条规则的关键词集合求交集，
     * 取交集最大的规则对应的科室。若并列，取权重高的。
     *
     * @param symptom 症状描述
     * @return 匹配结果
     */
    public FallbackResult match(String symptom) {
        // 提取症状中的关键词
        Set<String> symptomKeywords = extractKeywords(symptom);
        log.info("[降级匹配] 症状关键词: {}", symptomKeywords);

        // 遍历规则，计算每条规则的匹配度
        RuleMatch bestMatch = null;
        for (SymptomRule rule : RULES) {
            Set<String> intersection = new HashSet<>(rule.keywords);
            intersection.retainAll(symptomKeywords);
            int matchCount = intersection.size();

            if (matchCount == 0) continue;

            if (bestMatch == null
                    || matchCount > bestMatch.matchCount
                    || (matchCount == bestMatch.matchCount && rule.weight > bestMatch.weight)) {
                bestMatch = new RuleMatch(rule.deptName, matchCount, rule.weight);
            }
        }

        if (bestMatch != null) {
            BigDecimal confidence = calculateConfidence(bestMatch.matchCount, bestMatch.weight);
            log.info("[降级匹配] 推荐科室: {} (匹配 {} 词, 权重 {}, 置信度 {})",
                    bestMatch.deptName, bestMatch.matchCount, bestMatch.weight, confidence);
            return new FallbackResult(bestMatch.deptName, confidence);
        }

        // 无匹配时返回内科作为默认
        log.warn("[降级匹配] 无匹配规则，返回默认科室: 内科");
        return new FallbackResult("内科", BigDecimal.valueOf(50.00));
    }

    /**
     * 从 AI 返回原文中提取科室名称（作为降级解析方式）
     */
    public String extractDeptName(String rawText) {
        if (rawText == null || rawText.isBlank()) return "内科";

        String[] deptNames = {"呼吸内科", "消化内科", "神经内科", "内分泌科", "心内科",
                "内科", "外科", "儿科", "妇产科", "骨科", "眼科",
                "耳鼻喉科", "皮肤科", "口腔科"};

        // 按名称长度降序匹配，优先匹配更长的名称（如"呼吸内科"优先于"内科"）
        for (String deptName : deptNames) {
            if (rawText.contains(deptName)) {
                return deptName;
            }
        }
        return "内科";
    }

    /**
     * 简单关键词提取
     * <p>
     * 从症状文本中提取医学相关关键词，匹配预置规则中的词汇。
     */
    private Set<String> extractKeywords(String symptom) {
        Set<String> found = new HashSet<>();
        if (symptom == null || symptom.isBlank()) return found;

        // 收集所有规则中的关键词
        Set<String> allKeywords = new HashSet<>();
        for (SymptomRule rule : RULES) {
            allKeywords.addAll(rule.keywords);
        }

        // 在症状文本中查找匹配的关键词
        for (String keyword : allKeywords) {
            if (symptom.contains(keyword)) {
                found.add(keyword);
            }
        }
        return found;
    }

    /**
     * 根据匹配词数和规则权重计算置信度
     */
    private BigDecimal calculateConfidence(int matchCount, int weight) {
        double base = Math.min(matchCount * 25.0, 75.0);   // 匹配词数贡献最多 75%
        double bonus = weight * 2.5;                         // 权重贡献
        return BigDecimal.valueOf(Math.min(base + bonus, 95.00))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ==================== 内部类 ====================

    /**
     * 症状规则
     */
    private static class SymptomRule {
        final Set<String> keywords;
        final String deptName;
        final int weight;

        SymptomRule(Set<String> keywords, String deptName, int weight) {
            this.keywords = keywords;
            this.deptName = deptName;
            this.weight = weight;
        }
    }

    /**
     * 匹配结果
     */
    private static class RuleMatch {
        final String deptName;
        final int matchCount;
        final int weight;

        RuleMatch(String deptName, int matchCount, int weight) {
            this.deptName = deptName;
            this.matchCount = matchCount;
            this.weight = weight;
        }
    }
}
