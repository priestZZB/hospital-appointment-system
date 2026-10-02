package com.hospital.ai.controller;

import com.hospital.ai.mapper.B2Mapper;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 智能层其余算法接口（迭代16 B2-2 候诊时长 / B2-3 门诊量 / B2-4 AI 用药 / B2-5 报告摘要，
 * base=/api/ai）。预测结果统一落 ai_prediction 留痕。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class B2Controller {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 用药推荐规则：诊断关键词 → 推荐药品 */
    private static final Map<Set<String>, List<String>> DRUG_RULES = new LinkedHashMap<>();

    static {
        DRUG_RULES.put(Set.of("高血压", "血压升高", "血压高"), List.of("苯磺酸氨氯地平片（CCB）", "缬沙坦胶囊（ARB）", "氢氯噻嗪片（利尿剂）"));
        DRUG_RULES.put(Set.of("糖尿病", "血糖高", "血糖升高"), List.of("二甲双胍片（双胍类）", "格列美脲片（磺脲类）"));
        DRUG_RULES.put(Set.of("感染", "发热", "炎症"), List.of("阿莫西林胶囊（青霉素类，需皮试）", "布洛芬缓释胶囊（解热镇痛）"));
        DRUG_RULES.put(Set.of("胃炎", "胃痛", "反酸"), List.of("奥美拉唑肠溶胶囊（PPI）", "铝碳酸镁咀嚼片（胃黏膜保护）"));
    }

    private final B2Mapper b2Mapper;

    /** B2-2 候诊时长预测：body = { queueLength, avgMinutes, windows? } */
    @PostMapping("/wait-time/predict")
    @AuditLog(value = "候诊时长预测", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AI_PREDICT)
    public Result<Map<String, Object>> waitTime(@RequestBody Map<String, Object> body) {
        int queueLength = toInt(body.get("queueLength"));
        int avgMinutes = toInt(body.get("avgMinutes"));
        if (queueLength <= 0 || avgMinutes <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "queueLength 与 avgMinutes 须为正数");
        }
        int windows = body.get("windows") == null ? 1 : Math.max(1, toInt(body.get("windows")));
        double base = (double) queueLength * avgMinutes / windows;
        // 波动区间 ±20%
        double low = Math.round(base * 0.8);
        double high = Math.round(base * 1.2);
        double estimate = Math.round(base);
        String detail = String.format("queue=%d,avgMin=%d,windows=%d → estimate=%.0fmin range[%.0f,%.0f]",
                queueLength, avgMinutes, windows, estimate, low, high);
        b2Mapper.insertPrediction(predNo("WT"), "WAIT_TIME", estimate / 60.0, detail);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("estimateMinutes", estimate);
        data.put("rangeLow", low);
        data.put("rangeHigh", high);
        data.put("detail", detail);
        return Result.ok(data);
    }

    /** B2-3 门诊量预测：body = { recentDaily: [近7天门诊量] }，移动平均+趋势外推 3 天 */
    @PostMapping("/visit-volume/predict")
    @AuditLog(value = "门诊量预测", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AI_PREDICT)
    public Result<Map<String, Object>> visitVolume(@RequestBody Map<String, Object> body) {
        Object arr = body.get("recentDaily");
        if (!(arr instanceof List<?> list) || list.size() < 3) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "recentDaily 至少需要 3 天数据");
        }
        List<Double> series = new ArrayList<>();
        for (Object o : list) {
            series.add(o == null ? 0.0 : Double.parseDouble(String.valueOf(o)));
        }
        int n = series.size();
        double ma = 0;
        for (double v : series) {
            ma += v;
        }
        ma /= n;
        // 简单趋势：后半段均值 - 前半段均值
        double head = 0;
        double tail = 0;
        for (int i = 0; i < n / 2; i++) {
            head += series.get(i);
        }
        for (int i = n / 2; i < n; i++) {
            tail += series.get(i);
        }
        double trend = (tail / Math.max(1, n - n / 2)) - (head / Math.max(1, n / 2));
        trend = Math.max(-ma * 0.3, Math.min(ma * 0.3, trend)); // 趋势限幅 ±30%
        List<Double> forecast = new ArrayList<>();
        for (int d = 1; d <= 3; d++) {
            forecast.add((double) Math.max(0, Math.round(ma + trend * d)));
        }
        String detail = String.format("recent=%s → ma=%.1f trend=%.2f forecast=%s", series, ma, trend, forecast);
        b2Mapper.insertPrediction(predNo("VV"), "VISIT_VOLUME", ma, detail);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("movingAverage", Math.round(ma * 10) / 10.0);
        data.put("dailyTrend", Math.round(trend * 10) / 10.0);
        data.put("forecastNext3Days", forecast);
        data.put("detail", detail);
        return Result.ok(data);
    }

    /** B2-4 AI 用药推荐：body = { diagnosis, allergy? } */
    @PostMapping("/drug-recommend")
    @AuditLog(value = "AI用药推荐", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AI_DRUG)
    public Result<Map<String, Object>> drugRecommend(@RequestBody Map<String, Object> body) {
        String diagnosis = str(body.get("diagnosis"));
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "diagnosis 不能为空");
        }
        String allergy = str(body.get("allergy"));
        List<String> recommended = new ArrayList<>();
        String matched = null;
        for (Map.Entry<Set<String>, List<String>> e : DRUG_RULES.entrySet()) {
            for (String kw : e.getKey()) {
                if (diagnosis.contains(kw)) {
                    recommended = e.getValue();
                    matched = kw;
                    break;
                }
            }
            if (!recommended.isEmpty()) {
                break;
            }
        }
        if (allergy != null && !allergy.isBlank() && !recommended.isEmpty()) {
            recommended = recommended.stream()
                    .filter(d -> !d.toLowerCase().contains(allergy.toLowerCase()))
                    .toList();
        }
        String detail = String.format("diagnosis=%s matched=%s → %s", diagnosis, matched, recommended);
        b2Mapper.insertPrediction(predNo("DR"), "DRUG_RECOMMEND", recommended.isEmpty() ? 0.0 : 1.0, detail);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchedKeyword", matched);
        data.put("recommended", recommended);
        data.put("disclaimer", "推荐结果仅供医生参考，实际处方须由执业医师审核开具");
        return Result.ok(data);
    }

    /** B2-5 报告摘要：body = { title, chiefComplaint, diagnosis, advice } */
    @PostMapping("/report-summary")
    @AuditLog(value = "AI报告摘要生成", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AI_SUMMARY)
    public Result<Map<String, Object>> reportSummary(@RequestBody Map<String, Object> body) {
        String title = str(body.get("title"));
        String chief = str(body.get("chiefComplaint"));
        String diagnosis = str(body.get("diagnosis"));
        String advice = str(body.get("advice"));
        if (chief == null || diagnosis == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "chiefComplaint 与 diagnosis 不能为空");
        }
        StringBuilder summary = new StringBuilder();
        summary.append(title == null || title.isBlank() ? "门诊" : title).append("摘要：患者因「")
                .append(trunc(chief, 60)).append("」就诊，诊断为「").append(trunc(diagnosis, 60)).append("」");
        if (advice != null && !advice.isBlank()) {
            summary.append("，医嘱建议：").append(trunc(advice, 80));
        }
        summary.append("。");
        // 关键词抽取（去重高频词，长度>=2）
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String field : new String[]{chief, diagnosis, advice}) {
            if (field == null) {
                continue;
            }
            for (int i = 0; i < field.length() - 1; i += 2) {
                String word = field.substring(i, Math.min(field.length(), i + 2));
                freq.merge(word, 1, Integer::sum);
            }
        }
        List<String> keywords = freq.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
        b2Mapper.insertPrediction(predNo("RS"), "REPORT_SUMMARY", (double) summary.length(), summary.toString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary.toString());
        data.put("keywords", keywords);
        return Result.ok(data);
    }

    /** 预测记录分页（按类型） */
    @GetMapping("/predictions")
    @RequiresPermission(PermissionConstant.AI_PREDICT)
    public Result<Map<String, Object>> predictions(@RequestParam(value = "predType", required = false) String predType,
                                                   @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", b2Mapper.selectPredictionPage(predType, (pageNo - 1) * pageSize, pageSize));
        page.put("total", b2Mapper.countPredictionPage(predType));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    private String predNo(String prefix) {
        return prefix + LocalDateTime.now().format(TS) + String.format("%03d", RANDOM.nextInt(1000));
    }

    private String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private int toInt(Object v) {
        return v == null ? 0 : Integer.parseInt(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
