package com.hospital.ai.service;

import com.hospital.ai.entity.AiCallLog;
import com.hospital.ai.mapper.AiCallLogMapper;
import com.hospital.ai.vo.TriageResultVO;
import com.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiService 单元测试
 * <p>
 * 覆盖：正常 AI 返回、AI 超时降级、AI 异常降级、无匹配关键词默认内科。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiService 单元测试")
class AiServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private FallbackService fallbackService;

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @InjectMocks
    private AiService aiService;

    private static final Long PATIENT_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.options(any())).thenReturn(requestSpec);
        lenient().when(aiCallLogMapper.insert(any())).thenReturn(1);
    }

    @Nested
    @DisplayName("正常 AI 返回")
    class NormalAiResponse {

        @Test
        @DisplayName("AI 成功返回推荐科室")
        void shouldReturnDeptWhenAiRespondsNormally() {
            // given
            String responseJson = "{\"deptName\":\"呼吸内科\",\"confidence\":92}";
            when(callResponseSpec.chatResponse()).thenReturn(createChatResponse(responseJson));
            when(requestSpec.call()).thenReturn(callResponseSpec);

            // when
            TriageResultVO result = aiService.triage(PATIENT_ID, "发烧咳嗽喉咙痛");

            // then
            assertNotNull(result);
            assertEquals("呼吸内科", result.getDeptName());
            assertEquals(Long.valueOf(11L), result.getDeptId());
            assertFalse(result.getIsDegraded());
            assertNull(result.getDegradedReason());

            // 验证日志被保存
            ArgumentCaptor<AiCallLog> logCaptor = ArgumentCaptor.forClass(AiCallLog.class);
            verify(aiCallLogMapper).insert(logCaptor.capture());
            AiCallLog savedLog = logCaptor.getValue();
            assertEquals(PATIENT_ID, savedLog.getPatientId());
            assertEquals("deepseek-chat", savedLog.getModelName());
            assertEquals("呼吸内科", savedLog.getRecommendDeptName());
            assertEquals(0, savedLog.getIsDegraded());
            assertEquals(1, savedLog.getStatus());
        }
    }

    @Nested
    @DisplayName("AI 超时降级")
    class TimeoutFallback {

        @Test
        @DisplayName("AI 超时 3 秒后降级到关键词匹配")
        void shouldFallbackOnTimeout() {
            // given — AI 调用阻塞超 3 秒
            when(requestSpec.call()).thenAnswer(inv -> {
                Thread.sleep(4000);
                return callResponseSpec;
            });
            FallbackService.FallbackResult fallbackResult =
                    new FallbackService.FallbackResult("呼吸内科", BigDecimal.valueOf(85.00));
            when(fallbackService.match(anyString())).thenReturn(fallbackResult);

            // when
            TriageResultVO result = aiService.triage(PATIENT_ID, "发烧咳嗽喉咙痛");

            // then
            assertNotNull(result);
            assertEquals("呼吸内科", result.getDeptName());
            assertTrue(result.getIsDegraded());
            assertNotNull(result.getDegradedReason());
            assertEquals("TIMEOUT", result.getDegradedReason());

            // 验证降级日志
            ArgumentCaptor<AiCallLog> logCaptor = ArgumentCaptor.forClass(AiCallLog.class);
            verify(aiCallLogMapper).insert(logCaptor.capture());
            AiCallLog savedLog = logCaptor.getValue();
            assertEquals(1, savedLog.getIsDegraded());
            assertEquals("TIMEOUT", savedLog.getDegradedReason());
        }
    }

    @Nested
    @DisplayName("AI 调用异常降级")
    class ErrorFallback {

        @Test
        @DisplayName("AI 调用抛异常时降级到关键词匹配")
        void shouldFallbackOnApiError() {
            // given
            when(requestSpec.call()).thenThrow(new RuntimeException("Connection refused"));
            FallbackService.FallbackResult fallbackResult =
                    new FallbackService.FallbackResult("心内科", BigDecimal.valueOf(80.00));
            when(fallbackService.match(anyString())).thenReturn(fallbackResult);

            // when
            TriageResultVO result = aiService.triage(PATIENT_ID, "胸痛心悸气短");

            // then
            assertNotNull(result);
            assertEquals("心内科", result.getDeptName());
            assertTrue(result.getIsDegraded());
            assertEquals("API_ERROR", result.getDegradedReason());
        }
    }

    @Nested
    @DisplayName("降级服务：关键词匹配")
    class KeywordMatch {

        private FallbackService fallbackService;

        @BeforeEach
        void setUp() {
            fallbackService = new FallbackService();
        }

        @Test
        @DisplayName("多关键词匹配返回最高交集科室")
        void shouldMatchByKeywordIntersection() {
            var result = fallbackService.match("我发烧咳嗽喉咙很痛已经三天了");
            assertEquals("呼吸内科", result.deptName());
        }

        @Test
        @DisplayName("症状匹配骨科关键词")
        void shouldMatchOrthopedics() {
            var result = fallbackService.match("骨折扭伤关节痛");
            assertEquals("骨科", result.deptName());
        }

        @Test
        @DisplayName("无匹配关键词时返回默认内科")
        void shouldReturnDefaultWhenNoMatch() {
            var result = fallbackService.match("不舒服");
            assertEquals("内科", result.deptName());
        }

        @Test
        @DisplayName("多个规则匹配时取交集最大的")
        void shouldPickMaxIntersection() {
            // "发烧咳嗽" 匹配 "发烧+咳嗽+喉咙痛"(2个) 和 "发烧+咳嗽+呼吸困难"(2个)，
            // 但后者权重更高 → 应选后者 → 呼吸内科
            var result = fallbackService.match("发烧咳嗽呼吸困难");
            assertEquals("呼吸内科", result.deptName());
        }

        @Test
        @DisplayName("extractDeptName 从文本中正确提取科室名")
        void shouldExtractDeptNameFromText() {
            // 长名称优先匹配
            assertEquals("呼吸内科", fallbackService.extractDeptName("建议前往呼吸内科就诊"));
            assertEquals("心内科", fallbackService.extractDeptName("可能是心内科的问题"));
            assertEquals("内科", fallbackService.extractDeptName("建议去看内科"));
        }

        @Test
        @DisplayName("extractDeptName 空文本返回默认内科")
        void shouldReturnDefaultForEmptyText() {
            assertEquals("内科", fallbackService.extractDeptName(""));
            assertEquals("内科", fallbackService.extractDeptName(null));
        }

        @Test
        @DisplayName("儿童发烧应匹配儿科")
        void shouldMatchPediatricsForChild() {
            var result = fallbackService.match("儿童发烧39度");
            assertEquals("儿科", result.deptName());
        }
    }

    // ==================== 辅助方法 ====================

    private ChatResponse createChatResponse(String content) {
        // AssistantMessage 的 getText() 是 AbstractMessage 的 final 方法，Mockito 无法 stub
        // 直接用 concrete instance 而非 mock
        AssistantMessage output = new AssistantMessage(content);
        Generation generation = new Generation(output);
        return new ChatResponse(List.of(generation));
    }
}
