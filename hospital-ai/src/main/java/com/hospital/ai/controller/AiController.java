package com.hospital.ai.controller;

import com.hospital.ai.dto.TriageRequestDTO;
import com.hospital.ai.service.AiService;
import com.hospital.ai.vo.TriageResultVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能分诊接口
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * AI 智能分诊
     * <p>
     * 患者输入自然语言症状描述，推荐最合适的就诊科室。
     * 优先调用 deepseek 大模型，3 秒超时后自动降级为关键词匹配。
     *
     * @param dto 症状描述（2~500字）
     * @return 推荐科室 + 置信度 + 是否降级
     */
    @AuditLog(value = "AI智能分诊", operationType = "AI_TRIAGE")
    @PostMapping("/triage")
    public Result<TriageResultVO> triage(@Valid @RequestBody TriageRequestDTO dto) {
        Long patientId = UserContext.getUserId();
        TriageResultVO result = aiService.triage(patientId, dto.getSymptom());
        log.info("[AI分诊] patientId={}, dept={}, confidence={}, degraded={}",
                patientId, result.getDeptName(), result.getConfidence(), result.getIsDegraded());
        return Result.ok(result);
    }
}
