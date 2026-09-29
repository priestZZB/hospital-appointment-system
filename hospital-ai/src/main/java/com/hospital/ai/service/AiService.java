package com.hospital.ai.service;

import com.hospital.ai.entity.AiCallLog;
import com.hospital.ai.mapper.AiCallLogMapper;
import com.hospital.ai.vo.TriageResultVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 分诊服务
 * <p>
 * 调用 deepseek 大模型推荐科室，3 秒超时后自动降级为关键词匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient.Builder chatClientBuilder;
    private final FallbackService fallbackService;
    private final AiCallLogMapper aiCallLogMapper;

    /**
     * AI 智能分诊
     *
     * @param patientId  患者 ID
     * @param symptom    症状描述
     * @return 分诊结果
     */
    public TriageResultVO triage(Long patientId, String symptom) {
        long startTime = System.currentTimeMillis();

        CompletableFuture<TriageResultVO> future = CompletableFuture.supplyAsync(() -> {
            String prompt = buildPrompt(symptom);
            ChatClient chatClient = chatClientBuilder.build();
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder()
                            .model("deepseek-chat")
                            .temperature(0.3)
                            .build())
                    .call()
                    .chatResponse();
            return parseAiResponse(response, symptom, prompt, startTime, patientId);
        });

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // 主动取消后台线程，避免继续消耗 API token
            log.warn("[AI] 调用超时，降级为关键词匹配: symptom={}", symptom);
            return fallbackWithLog(patientId, symptom, startTime, "TIMEOUT");
        } catch (Exception e) {
            future.cancel(true);
            log.error("[AI] 调用异常，降级为关键词匹配: {}", e.getMessage());
            return fallbackWithLog(patientId, symptom, startTime, "API_ERROR");
        }
    }

    /**
     * 构建发送给 deepseek 的 Prompt
     */
    private String buildPrompt(String symptom) {
        return """
                你是一个医疗分诊助手。根据以下患者症状描述，推荐最合适的就诊科室。
                规则：
                1. 只返回科室名称，不要解释
                2. 可选科室为：内科、外科、儿科、妇产科、骨科、眼科、耳鼻喉科、皮肤科、神经内科、心内科、呼吸内科、消化内科、内分泌科、口腔科
                3. 同时返回一个 0-100 的置信度分数
                4. 返回格式严格为 JSON: {"deptName":"科室名","confidence":85}

                患者症状：%s
                """.formatted(symptom);
    }

    /**
     * 解析 AI 返回结果
     */
    private TriageResultVO parseAiResponse(ChatResponse response, String symptom, String prompt,
                                            long startTime, Long patientId) {
        long elapsed = System.currentTimeMillis() - startTime;
        String responseText = response.getResult().getOutput().getText();
        log.info("[AI] deepseek 返回: {} (耗时 {}ms)", responseText, elapsed);

        // 解析 AI 返回的 JSON
        String deptName;
        BigDecimal confidence;
        try {
            // 简单 JSON 解析，避免引入额外依赖
            String json = responseText.trim();
            if (json.startsWith("")) {
                json = json.replaceAll("\\w*", "").replaceAll("", "").trim();
            }
            deptName = extractJsonValue(json, "deptName");
            String confidenceStr = extractJsonValue(json, "confidence");
            confidence = new BigDecimal(confidenceStr).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[AI] 解析响应失败，尝试从原文提取: {}", responseText);
            deptName = fallbackService.extractDeptName(responseText);
            confidence = BigDecimal.valueOf(70.00);
        }

        // 保存成功日志
        saveLog(patientId, symptom, "deepseek-chat", prompt, responseText,
                deptName, confidence, 0, null, elapsed, 1, null);

        return TriageResultVO.builder()
                .deptId(getDeptId(deptName))
                .deptName(deptName)
                .confidence(confidence)
                .isDegraded(false)
                .executionTimeMs(elapsed)
                .build();
    }

    /**
     * 降级 + 记录日志
     */
    private TriageResultVO fallbackWithLog(Long patientId, String symptom, long startTime, String reason) {
        long elapsed = System.currentTimeMillis() - startTime;
        FallbackService.FallbackResult fallbackResult = fallbackService.match(symptom);

        // 保存降级日志
        saveLog(patientId, symptom, "deepseek-chat", null, null,
                fallbackResult.deptName(), fallbackResult.confidence(),
                1, reason, elapsed, 1, null);

        return TriageResultVO.builder()
                .deptId(getDeptId(fallbackResult.deptName()))
                .deptName(fallbackResult.deptName())
                .confidence(fallbackResult.confidence())
                .isDegraded(true)
                .degradedReason(reason)
                .executionTimeMs(elapsed)
                .build();
    }

    /**
     * 保存 AI 调用日志
     */
    private void saveLog(Long patientId, String symptom, String modelName,
                         String requestText, String responseText,
                         String recommendDeptName, BigDecimal confidence,
                         int isDegraded, String degradedReason,
                         long executionTimeMs, int status, String errorMessage) {
        try {
            AiCallLog log = new AiCallLog();
            log.setPatientId(patientId);
            log.setSymptomInput(symptom);
            log.setModelName(modelName);
            log.setRequestText(requestText);
            log.setResponseText(responseText);
            log.setRecommendDeptId(getDeptId(recommendDeptName));
            log.setRecommendDeptName(recommendDeptName);
            log.setConfidence(confidence);
            log.setIsDegraded(isDegraded);
            log.setDegradedReason(degradedReason);
            log.setExecutionTimeMs(executionTimeMs);
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            aiCallLogMapper.insert(log);
        } catch (Exception e) {
            log.error("[AI] 保存调用日志失败", e);
        }
    }

    /**
     * 根据科室名称返回科室 ID（14 科室硬编码映射）
     */
    private Long getDeptId(String deptName) {
        if (deptName == null) return null;
        return switch (deptName) {
            case "内科" -> 1L;
            case "外科" -> 2L;
            case "儿科" -> 3L;
            case "妇产科" -> 4L;
            case "骨科" -> 5L;
            case "眼科" -> 6L;
            case "耳鼻喉科" -> 7L;
            case "皮肤科" -> 8L;
            case "神经内科" -> 9L;
            case "心内科" -> 10L;
            case "呼吸内科" -> 11L;
            case "消化内科" -> 12L;
            case "内分泌科" -> 13L;
            case "口腔科" -> 14L;
            default -> null;
        };
    }

    /**
     * 从 JSON 字符串中提取值（简易实现）
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) {
            throw new BusinessException(ErrorCodeEnum.AI_SERVICE_DEGRADED, "无法解析AI返回结果");
        }
        int colonIdx = json.indexOf(":", keyIdx);
        if (colonIdx == -1) {
            throw new BusinessException(ErrorCodeEnum.AI_SERVICE_DEGRADED, "无法解析AI返回结果");
        }
        String value = json.substring(colonIdx + 1).trim();
        // 去掉首尾引号和逗号/大括号
        value = value.replaceAll("^[\"{]", "").replaceAll("[\"}]$", "");
        // 如果还有尾部引号或逗号
        value = value.replaceAll("[\",}].*$", "").trim();
        return value;
    }
}
