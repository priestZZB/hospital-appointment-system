package com.hospital.common.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * <p>
 * 覆盖：签发 → 验签 → 解析 → 过期 Token → 无效 Token
 */
@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 使用固定的 secret 和 expiration 构造，确保测试可重复
        jwtUtil = new JwtUtil("test-secret-key-for-junit-testing-2026", 3600);
    }

    // ==================== 正常签发与解析 ====================

    @Nested
    @DisplayName("签发 → 验签 → 解析")
    class GenerateAndParse {

        @Test
        @DisplayName("正常签发并解析出 userId")
        void testGenerateAndGetUserId() {
            String token = jwtUtil.generate(1L, List.of("PATIENT"));
            assertNotNull(token);

            Long userId = jwtUtil.getUserId(token);
            assertEquals(1L, userId);
        }

        @Test
        @DisplayName("正常签发并解析出 roles")
        void testGenerateAndGetRoles() {
            List<String> roles = List.of("PATIENT", "ADMIN");
            String token = jwtUtil.generate(2L, roles);

            List<String> parsedRoles = jwtUtil.getRoles(token);
            assertEquals(2, parsedRoles.size());
            assertTrue(parsedRoles.contains("PATIENT"));
            assertTrue(parsedRoles.contains("ADMIN"));
        }

        @Test
        @DisplayName("签发 Token 可被 isValid() 验证")
        void testGenerateAndIsValid() {
            String token = jwtUtil.generate(3L, List.of("DOCTOR"));
            assertTrue(jwtUtil.isValid(token));
        }

        @Test
        @DisplayName("新签发 Token 未过期")
        void testGenerateAndIsNotExpired() {
            String token = jwtUtil.generate(4L, List.of("PATIENT"));
            assertFalse(jwtUtil.isExpired(token));
        }

        @Test
        @DisplayName("签发时 roles 为 null 不抛异常")
        void testGenerateWithNullRoles() {
            String token = jwtUtil.generate(5L, null);
            assertNotNull(token);
            List<String> parsedRoles = jwtUtil.getRoles(token);
            assertNotNull(parsedRoles);
            assertTrue(parsedRoles.isEmpty());
        }

        @Test
        @DisplayName("签发时 roles 为空列表")
        void testGenerateWithEmptyRoles() {
            String token = jwtUtil.generate(6L, Collections.emptyList());
            assertNotNull(token);
            List<String> parsedRoles = jwtUtil.getRoles(token);
            assertNotNull(parsedRoles);
            assertTrue(parsedRoles.isEmpty());
        }
    }

    // ==================== 无效 Token ====================

    @Nested
    @DisplayName("无效 Token 校验")
    class InvalidToken {

        @Test
        @DisplayName("伪造 Token 被 isValid() 拒绝")
        void testInvalidToken() {
            assertFalse(jwtUtil.isValid("invalid.token.here"));
        }

        @Test
        @DisplayName("错误签名的 Token 被 isValid() 拒绝")
        void testTokenWithWrongSecret() {
            // 用另一个密钥签发
            JwtUtil otherJwtUtil = new JwtUtil("different-secret-key-for-testing-2026", 3600);
            String token = otherJwtUtil.generate(1L, List.of("PATIENT"));

            // 当前 jwtUtil 无法验签
            assertFalse(jwtUtil.isValid(token));
        }

        @Test
        @DisplayName("空字符串 Token 被拒绝")
        void testEmptyToken() {
            assertFalse(jwtUtil.isValid(""));
        }

        @Test
        @DisplayName("null Token parse 时抛异常")
        void testNullToken() {
            assertThrows(JwtException.class, () -> jwtUtil.parse(null));
        }

        @Test
        @DisplayName("伪造 Token 被 isExpired() 判定为未过期（仅格式错误）")
        void testInvalidTokenIsNotExpired() {
            // isExpired 仅捕获 ExpiredJwtException, 格式错误返回 false
            assertFalse(jwtUtil.isExpired("invalid.token.here"));
        }
    }

    // ==================== 过期 Token ====================

    @Nested
    @DisplayName("过期 Token 校验")
    class ExpiredToken {

        @Test
        @DisplayName("已过期 Token 被 isExpired() 识别")
        void testExpiredToken() {
            // 使用极短的过期时间（1秒），立刻过期
            JwtUtil shortJwtUtil = new JwtUtil("test-secret-key-for-junit-testing-2026", -1);
            String token = shortJwtUtil.generate(1L, List.of("PATIENT"));

            assertTrue(shortJwtUtil.isExpired(token));
        }

        @Test
        @DisplayName("已过期 Token 被 isValid() 拒绝")
        void testExpiredTokenIsInvalid() {
            JwtUtil shortJwtUtil = new JwtUtil("test-secret-key-for-junit-testing-2026", -1);
            String token = shortJwtUtil.generate(1L, List.of("PATIENT"));

            assertFalse(shortJwtUtil.isValid(token));
        }

        @Test
        @DisplayName("过期 Token 剩余秒数为 0")
        void testExpiredTokenRemainingSeconds() {
            JwtUtil shortJwtUtil = new JwtUtil("test-secret-key-for-junit-testing-2026", -1);
            String token = shortJwtUtil.generate(1L, List.of("PATIENT"));

            assertEquals(0, shortJwtUtil.getRemainingSeconds(token));
        }
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("超大 userId 可正常签发解析")
        void testLargeUserId() {
            Long largeId = Long.MAX_VALUE;
            String token = jwtUtil.generate(largeId, List.of("PATIENT"));
            assertEquals(largeId, jwtUtil.getUserId(token));
        }

        @Test
        @DisplayName("正常 Token 剩余秒数 > 0")
        void testRemainingSecondsPositive() {
            String token = jwtUtil.generate(1L, List.of("PATIENT"));
            long remaining = jwtUtil.getRemainingSeconds(token);
            assertTrue(remaining > 0);
            assertTrue(remaining <= 3600);
        }

        @Test
        @DisplayName("roles 中包含特殊字符可正常解析")
        void testRolesWithSpecialCharacters() {
            List<String> roles = List.of("ROLE_DOCTOR", "ROLE_ADMIN");
            String token = jwtUtil.generate(1L, roles);
            List<String> parsedRoles = jwtUtil.getRoles(token);
            assertEquals(2, parsedRoles.size());
        }
    }
}
