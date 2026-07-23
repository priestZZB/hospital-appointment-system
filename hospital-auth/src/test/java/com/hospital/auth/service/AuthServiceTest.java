package com.hospital.auth.service;

import com.hospital.auth.dto.LoginDTO;
import com.hospital.auth.dto.RegisterDTO;
import com.hospital.auth.entity.Role;
import com.hospital.auth.entity.User;
import com.hospital.auth.mapper.RoleMapper;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.auth.vo.LoginVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.dto.CreatePatientDTO;
import com.hospital.common.util.JwtUtil;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试
 * <p>
 * 覆盖：注册成功、重复注册、登录成功、密码错误、登出、Token 刷新。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PatientFeignClient patientFeignClient;

    @InjectMocks
    private AuthService authService;

    private static final String TEST_PHONE = "13800001111";
    private static final String TEST_PASSWORD = "test123";
    private static final String TEST_NAME = "测试用户";
    private static final String TEST_TOKEN = "eyJhbGci.eyJzdWIi.testsig";

    @BeforeEach
    void setUp() {
        // lenient: 并非所有测试都使用 Redis / insert，严格模式会触发 UnnecessaryStubbingException
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 模拟 MyBatis useGeneratedKeys 主键回填行为
        lenient().doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));
    }

    // ==================== 注册 ====================

    @Nested
    @DisplayName("注册")
    class Register {

        @Test
        @DisplayName("正常注册成功")
        void testRegisterSuccess() {
            RegisterDTO dto = buildRegisterDTO();
            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(null);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("$2a$12$encrypted");
            when(roleMapper.selectByCode("ROLE_PATIENT")).thenReturn(buildPatientRole());
            when(roleMapper.insertUserRole(anyLong(), anyLong())).thenReturn(1);
            when(patientFeignClient.createPatient(any(CreatePatientDTO.class))).thenReturn(1L);

            assertDoesNotThrow(() -> authService.register(dto));
            verify(userMapper).insert(any(User.class));
            verify(roleMapper).insertUserRole(anyLong(), anyLong());
        }

        @Test
        @DisplayName("手机号已注册应抛出异常")
        void testRegisterDuplicatePhone() {
            RegisterDTO dto = buildRegisterDTO();
            User existing = new User();
            existing.setId(1L);
            existing.setPhone(TEST_PHONE);
            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(existing);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(dto));
            assertEquals(ErrorCodeEnum.PHONE_ALREADY_REGISTERED.getCode(), ex.getCode());
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("注册时 Feign 调用失败不阻断注册")
        void testRegisterFeignFailureDoesNotBlock() {
            RegisterDTO dto = buildRegisterDTO();
            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(null);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("$2a$12$encrypted");
            when(roleMapper.selectByCode("ROLE_PATIENT")).thenReturn(buildPatientRole());
            when(roleMapper.insertUserRole(anyLong(), anyLong())).thenReturn(1);
            when(patientFeignClient.createPatient(any(CreatePatientDTO.class)))
                    .thenThrow(FeignException.errorStatus("createPatient",
                            feign.Response.builder()
                                    .status(503)
                                    .reason("Service Unavailable")
                                    .request(Request.create(Request.HttpMethod.POST, "/api/patient/internal/create",
                                            Collections.emptyMap(), null, new RequestTemplate()))
                                    .build()));

            // 注册应成功，Feign 异常被捕获
            assertDoesNotThrow(() -> authService.register(dto));
        }
    }

    // ==================== 登录 ====================

    @Nested
    @DisplayName("登录")
    class Login {

        @Test
        @DisplayName("正常登录成功并返回 Token")
        void testLoginSuccess() {
            LoginDTO dto = buildLoginDTO();
            User user = buildUser();

            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(user);
            when(passwordEncoder.matches(TEST_PASSWORD, user.getPassword())).thenReturn(true);
            when(roleMapper.findByUserId(1L)).thenReturn(List.of(buildPatientRole()));
            when(jwtUtil.generate(1L, List.of("ROLE_PATIENT"))).thenReturn(TEST_TOKEN);
            when(userMapper.updateLastLogin(anyLong(), anyString())).thenReturn(1);

            LoginVO result = authService.login(dto, "127.0.0.1");

            assertNotNull(result);
            assertEquals(TEST_TOKEN, result.getToken());
            assertEquals(1L, result.getUserId());
            assertEquals(TEST_PHONE, result.getPhone());
            assertTrue(result.getRoles().contains("ROLE_PATIENT"));
        }

        @Test
        @DisplayName("密码错误应抛出异常")
        void testLoginWrongPassword() {
            LoginDTO dto = buildLoginDTO();
            User user = buildUser();

            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(user);
            when(passwordEncoder.matches(TEST_PASSWORD, user.getPassword())).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(dto, "127.0.0.1"));
            assertEquals(ErrorCodeEnum.PASSWORD_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("用户不存在应抛出异常")
        void testLoginUserNotFound() {
            LoginDTO dto = buildLoginDTO();
            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(dto, "127.0.0.1"));
            assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("用户被禁用应抛出异常")
        void testLoginUserDisabled() {
            LoginDTO dto = buildLoginDTO();
            User user = buildUser();
            user.setStatus(0); // 禁用
            when(userMapper.findByPhone(TEST_PHONE)).thenReturn(user);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(dto, "127.0.0.1"));
            assertEquals(ErrorCodeEnum.USER_DISABLED.getCode(), ex.getCode());
        }
    }

    // ==================== 登出 ====================

    @Nested
    @DisplayName("登出")
    class Logout {

        @Test
        @DisplayName("正常登出将 Token 加入黑名单")
        void testLogoutSuccess() {
            when(jwtUtil.getRemainingSeconds(TEST_TOKEN)).thenReturn(3600L);
            assertDoesNotThrow(() -> authService.logout(TEST_TOKEN));
            verify(valueOperations).set(eq("token:blacklist:" + TEST_TOKEN), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("空 Token 登出直接返回")
        void testLogoutEmptyToken() {
            assertDoesNotThrow(() -> authService.logout(null));
            assertDoesNotThrow(() -> authService.logout(""));
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("已过期 Token 不加入黑名单")
        void testLogoutExpiredToken() {
            when(jwtUtil.getRemainingSeconds(TEST_TOKEN)).thenReturn(0L);

            authService.logout(TEST_TOKEN);
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }

    // ==================== Token 刷新 ====================

    @Nested
    @DisplayName("Token 刷新")
    class Refresh {

        @Test
        @DisplayName("正常刷新返回新 Token")
        void testRefreshSuccess() {
            User user = buildUser();
            String newToken = "new." + TEST_TOKEN;

            when(jwtUtil.isValid(TEST_TOKEN)).thenReturn(true);
            when(jwtUtil.getUserId(TEST_TOKEN)).thenReturn(1L);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(jwtUtil.getRemainingSeconds(TEST_TOKEN)).thenReturn(1800L);
            when(roleMapper.findByUserId(1L)).thenReturn(List.of(buildPatientRole()));
            when(jwtUtil.generate(1L, List.of("ROLE_PATIENT"))).thenReturn(newToken);

            LoginVO result = authService.refresh(TEST_TOKEN);

            assertNotNull(result);
            assertEquals(newToken, result.getToken());
            // 旧 Token 应被加入黑名单
            verify(valueOperations).set(eq("token:blacklist:" + TEST_TOKEN), eq("1"), any(Duration.class));
        }

        @Test
        @DisplayName("无效 Token 刷新应抛出异常")
        void testRefreshInvalidToken() {
            when(jwtUtil.isValid(TEST_TOKEN)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(TEST_TOKEN));
            assertEquals(ErrorCodeEnum.TOKEN_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== 辅助方法 ====================

    private RegisterDTO buildRegisterDTO() {
        RegisterDTO dto = new RegisterDTO();
        dto.setPhone(TEST_PHONE);
        dto.setPassword(TEST_PASSWORD);
        dto.setRealName(TEST_NAME);
        dto.setGender(1);
        return dto;
    }

    private LoginDTO buildLoginDTO() {
        LoginDTO dto = new LoginDTO();
        dto.setPhone(TEST_PHONE);
        dto.setPassword(TEST_PASSWORD);
        return dto;
    }

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setPhone(TEST_PHONE);
        user.setPassword("$2a$12$encrypted");
        user.setRealName(TEST_NAME);
        user.setGender(1);
        user.setUserType("PATIENT");
        user.setStatus(1);
        return user;
    }

    private Role buildPatientRole() {
        Role role = new Role();
        role.setId(3L);
        role.setRoleCode("ROLE_PATIENT");
        role.setRoleName("患者");
        role.setStatus(1);
        return role;
    }
}
