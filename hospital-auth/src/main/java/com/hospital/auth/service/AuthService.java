package com.hospital.auth.service;

import cn.hutool.core.util.StrUtil;
import com.hospital.auth.dto.LoginDTO;
import com.hospital.auth.dto.RegisterDTO;
import com.hospital.auth.entity.Role;
import com.hospital.auth.entity.User;
import com.hospital.auth.entity.Permission;
import com.hospital.auth.entity.Position;
import com.hospital.auth.mapper.RoleMapper;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.auth.mapper.PermissionMapper;
import com.hospital.auth.mapper.PositionMapper;
import com.hospital.auth.vo.LoginVO;
import com.hospital.auth.vo.MyPermissionsVO;
import com.hospital.auth.vo.UserVO;
import com.hospital.common.cache.PermissionCacheService;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.constant.RoleConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.dto.CreatePatientDTO;
import com.hospital.common.util.JwtUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务
 * <p>
 * 负责注册、登录、登出、Token 刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final PositionMapper positionMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final PatientFeignClient patientFeignClient;
    private final PermissionCacheService permissionCacheService;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 患者注册
     * <p>
     * 1. 校验手机号唯一性
     * 2. BCrypt 加密密码
     * 3. 插入用户记录
     * 4. 调用 patient-service 创建患者档案（失败不阻断注册）
     *
     * @param dto 注册信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        // 1. 手机号唯一性校验
        User existing = userMapper.findByPhone(dto.getPhone());
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.PHONE_ALREADY_REGISTERED);
        }

        // 2. 构建用户
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setGender(dto.getGender() != null ? dto.getGender() : 0);
        user.setUserType("PATIENT");
        user.setStatus(1);
        user.setNeedPasswordChange(0);

        // 3. 插入用户
        userMapper.insert(user);
        log.info("[注册] 用户创建成功: userId={}, phone={}", user.getId(), user.getPhone());

        // 4. 分配默认角色 ROLE_PATIENT
        Role patientRole = roleMapper.selectByCode("ROLE_PATIENT");
        if (patientRole == null) {
            log.error("[注册] 系统角色未初始化: roleCode=ROLE_PATIENT");
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "系统角色未初始化，请联系管理员");
        }
        roleMapper.insertUserRole(user.getId(), patientRole.getId());
        log.info("[注册] 角色分配: userId={}, role={}", user.getId(), patientRole.getRoleCode());

        // 5. 调用 patient-service 创建患者档案（内部 RPC，失败仅记录日志不阻断）
        try {
            CreatePatientDTO patientDTO = new CreatePatientDTO(user.getId(), user.getRealName(), user.getPhone());
            patientFeignClient.createPatient(patientDTO);
            log.info("[注册] 患者档案创建成功: userId={}", user.getId());
        } catch (FeignException e) {
            log.warn("[注册] 患者档案创建失败（patient-service 不可用，后续可补偿）: userId={}, error={}",
                    user.getId(), e.getMessage());
        }
    }

    /**
     * 账号登录
     * <p>
     * 1. 查用户
     * 2. 验状态
     * 3. 验密码
     * 4. 查角色
     * 5. 签发 JWT
     * 6. 更新最后登录信息
     *
     * @param dto  登录请求
     * @param ip   客户端 IP
     * @return 登录凭证
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto, String ip) {
        // 1. 查用户
        User user = userMapper.findByPhone(dto.getPhone());
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 2. 验状态
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.USER_DISABLED);
        }

        // 3. 验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.PASSWORD_ERROR);
        }

        // 4. 查角色
        List<Role> roles = roleMapper.findByUserId(user.getId());
        List<String> roleCodes = roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());

        // 5. 签发 JWT
        String token = jwtUtil.generate(user.getId(), roleCodes);

        // 6. 权限码写入 Redis 缓存（供 Gateway 注入 Header / 前端 my-permissions 使用）
        cacheUserPermissions(user.getId(), jwtUtil.getRemainingSeconds(token) * 1000L);

        // 7. 更新最后登录
        userMapper.updateLastLogin(user.getId(), ip);

        log.info("[登录] 登录成功: userId={}, phone={}", user.getId(), user.getPhone());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .phone(user.getPhone())
                .realName(user.getRealName())
                .roles(roleCodes)
                .build();
    }

    /**
     * 登出
     * <p>
     * 将当前 Token 加入 Redis 黑名单（独立 key，各自 TTL），Gateway 后续请求会自动拦截。
     *
     * @param token JWT Token
     */
    public void logout(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }

        long remaining = jwtUtil.getRemainingSeconds(token);
        if (remaining <= 0) {
            log.debug("[登出] Token 已过期，无需加入黑名单");
            return;
        }

        // 每个 token 独立 key，避免 TTL 互相覆盖
        stringRedisTemplate.opsForValue().set(
                TOKEN_BLACKLIST_PREFIX + token, "1", Duration.ofSeconds(remaining));
        log.info("[登出] Token 已加入黑名单，剩余有效秒数: {}", remaining);
    }

    /**
     * Token 刷新
     * <p>
     * 旧 Token 加入黑名单，下发新 Token（同用户同角色）。
     *
     * @param oldToken 旧 Token（需在有效期内）
     * @return 新登录凭证
     */
    public LoginVO refresh(String oldToken) {
        // 1. 验旧 Token
        if (!jwtUtil.isValid(oldToken)) {
            throw new BusinessException(ErrorCodeEnum.TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserId(oldToken);

        // 2. 查用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.USER_DISABLED);
        }

        // 3. 旧 Token 加黑名单（独立 key）
        long remaining = jwtUtil.getRemainingSeconds(oldToken);
        if (remaining > 0) {
            stringRedisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + oldToken, "1", Duration.ofSeconds(remaining));
        }

        // 4. 查角色（以 DB 最新数据为准）并签发新 Token
        List<Role> roles = roleMapper.findByUserId(userId);
        List<String> roleCodes = roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());

        String newToken = jwtUtil.generate(userId, roleCodes);

        // 5. 刷新权限缓存（角色变更后即时生效）
        cacheUserPermissions(userId, jwtUtil.getRemainingSeconds(newToken) * 1000L);

        log.info("[刷新] Token 刷新成功: userId={}", userId);
        return LoginVO.builder()
                .token(newToken)
                .userId(user.getId())
                .phone(user.getPhone())
                .realName(user.getRealName())
                .roles(roleCodes)
                .build();
    }

    /**
     * 查询当前用户权限码（GET /api/auth/my-permissions）
     * <p>
     * 优先读 Redis 缓存（登录时已写入）；缓存未命中时回源查询 DB 并回填缓存。
     * 返回按类型分组（api/menu/btn）的权限码列表，超管返回通配码。
     *
     * @param userId 当前登录用户 ID
     * @return 我的权限 VO
     */
    public MyPermissionsVO myPermissions(Long userId) {
        List<String> permCodes = loadUserPermissions(userId);
        boolean superAdmin = roleMapper.findByUserId(userId).stream()
                .anyMatch(r -> RoleConstant.SUPER_ADMIN.equals(r.getRoleCode()));

        List<String> apiPerms = permCodes.stream()
                .filter(p -> p.startsWith("api:") || PermissionConstant.ALL_PERMISSIONS.equals(p))
                .distinct()
                .collect(Collectors.toList());
        List<String> menuPerms = permCodes.stream()
                .filter(p -> p.startsWith("menu:"))
                .distinct()
                .collect(Collectors.toList());
        List<String> btnPerms = permCodes.stream()
                .filter(p -> p.startsWith("btn:"))
                .distinct()
                .collect(Collectors.toList());

        return MyPermissionsVO.builder()
                .userId(userId)
                .superAdmin(superAdmin)
                .permissions(permCodes)
                .apiPermissions(apiPerms)
                .menuPermissions(menuPerms)
                .btnPermissions(btnPerms)
                .build();
    }

    /**
     * 加载用户权限码：Redis 缓存优先，未命中回源 DB。
     */
    private List<String> loadUserPermissions(Long userId) {
        List<String> cached = permissionCacheService.getPermissions(userId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        try {
            List<Role> roles = roleMapper.findByUserId(userId);
            List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());
            List<String> permCodes;
            if (roles.stream().anyMatch(r -> RoleConstant.SUPER_ADMIN.equals(r.getRoleCode()))) {
                permCodes = List.of(PermissionConstant.ALL_PERMISSIONS);
            } else if (roleIds.isEmpty()) {
                permCodes = List.of();
            } else {
                permCodes = permissionMapper.findByRoleIds(roleIds).stream()
                        .map(Permission::getPermCode)
                        .distinct()
                        .collect(Collectors.toList());
            }
            // 回填缓存（默认 2 小时）
            permissionCacheService.cachePermissions(userId, permCodes, Duration.ofHours(2));
            return permCodes;
        } catch (Exception e) {
            log.warn("[权限] 权限码查询失败: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 当前登录用户个人信息（含岗位信息）
     * <p>
     * 岗位纯展示：返回岗位名称/职务，供个人信息页与工作台顶栏展示。
     *
     * @param userId 当前登录用户 ID
     * @return 个人信息 VO
     */
    public UserVO profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        List<String> roles = roleMapper.findByUserId(userId).stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setGender(user.getGender());
        vo.setUserType(user.getUserType());
        vo.setPositionId(user.getPositionId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoles(roles);
        if (user.getPositionId() != null) {
            Position position = positionMapper.selectById(user.getPositionId());
            if (position != null) {
                vo.setPositionName(position.getPositionName());
                vo.setPositionTitle(position.getTitle());
            }
        }
        return vo;
    }

    /**
     * 查询用户权限码并写入 Redis 缓存。
     * <p>
     * 超管通配：拥有 {@link RoleConstant#SUPER_ADMIN} 角色的用户写入通配码 {@code *:*:*}
     * （由拦截器判定角色即可放行，此处写入通配码便于前端「我的权限」展示）。
     *
     * @param userId    用户 ID
     * @param ttlMillis 缓存有效期（毫秒），与本次签发的 Token 有效期一致
     */
    private void cacheUserPermissions(Long userId, long ttlMillis) {
        try {
            List<Role> roles = roleMapper.findByUserId(userId);
            List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());
            List<String> permCodes;
            if (roles.stream().anyMatch(r -> RoleConstant.SUPER_ADMIN.equals(r.getRoleCode()))) {
                permCodes = List.of(PermissionConstant.ALL_PERMISSIONS);
            } else if (roleIds.isEmpty()) {
                permCodes = List.of();
            } else {
                permCodes = permissionMapper.findByRoleIds(roleIds).stream()
                        .map(Permission::getPermCode)
                        .distinct()
                        .collect(Collectors.toList());
            }
            long ttlSeconds = Math.max(ttlMillis / 1000, 300);
            permissionCacheService.cachePermissions(userId, permCodes, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("[权限缓存] 写入失败（不影响登录）: userId={}, error={}", userId, e.getMessage());
        }
    }
}
