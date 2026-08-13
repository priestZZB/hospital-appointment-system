package com.hospital.auth.service;

import com.hospital.auth.dto.UserPageQueryDTO;
import com.hospital.auth.dto.CreateUserDTO;
import com.hospital.auth.entity.User;
import com.hospital.auth.entity.Role;
import com.hospital.auth.mapper.RoleMapper;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.auth.vo.UserVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.dto.CreatePatientDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PatientFeignClient patientFeignClient;

    /**
     * 分页查询用户列表（含 total）
     *
     * @param dto 查询条件 + 分页参数
     * @return 分页结果（records + total）
     */
    public PageResult<UserVO> pageQuery(UserPageQueryDTO dto) {
        List<UserVO> list = userMapper.selectPage(dto);
        long total = userMapper.countPage(dto);
        return new PageResult<>(list, total, dto.getPageNo(), dto.getPageSize());
    }

    /**
     * 变更用户启用/禁用状态
     *
     * @param id     用户 ID
     * @param status 目标状态：1-启用 0-停用
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (status != 1 && status != 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态值只能为 0 或 1");
        }
        userMapper.updateStatus(id, status);
        log.info("[用户] 状态变更: userId={}, status={}", id, status);
    }

    /**
     * 创建用户（管理员创建医生/管理员/患者账号）
     * <p>
     * 1. 校验手机号唯一 + 用户类型合法
     * 2. 插入用户（BCrypt 加密）
     * 3. 分配角色：缺省按用户类型分配默认角色，额外可传 roleIds
     * 4. 患者类型同步创建患者档案（失败不阻断）
     *
     * @param dto 创建用户请求
     * @return 新用户信息（含角色列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(CreateUserDTO dto) {
        // 1. 手机号唯一性
        if (userMapper.findByPhone(dto.getPhone()) != null) {
            throw new BusinessException(ErrorCodeEnum.PHONE_ALREADY_REGISTERED);
        }

        // 2. 用户类型校验
        String userType = dto.getUserType() == null ? "" : dto.getUserType().trim().toUpperCase();
        if (!Set.of("PATIENT", "DOCTOR", "ADMIN").contains(userType)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户类型仅支持 PATIENT / DOCTOR / ADMIN");
        }

        // 3. 插入用户
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setGender(dto.getGender() != null ? dto.getGender() : 0);
        user.setUserType(userType);
        user.setStatus(1);
        user.setNeedPasswordChange(0);
        userMapper.insert(user);
        log.info("[用户] 创建用户成功: userId={}, phone={}, userType={}", user.getId(), user.getPhone(), userType);

        // 4. 分配角色（默认角色 + 显式角色，去重）
        Set<Long> roleIds = new LinkedHashSet<>();
        String defaultRoleCode = switch (userType) {
            case "DOCTOR" -> "ROLE_DOCTOR";
            case "ADMIN" -> "ROLE_ADMIN";
            default -> "ROLE_PATIENT";
        };
        Role defaultRole = roleMapper.selectByCode(defaultRoleCode);
        if (defaultRole != null) {
            roleIds.add(defaultRole.getId());
        }
        if (dto.getRoleIds() != null) {
            roleIds.addAll(dto.getRoleIds());
        }
        for (Long roleId : roleIds) {
            Role role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "角色不存在: id=" + roleId);
            }
            if (roleMapper.countUserRole(user.getId(), roleId) == 0) {
                roleMapper.insertUserRole(user.getId(), roleId);
            }
        }

        // 5. 患者类型同步创建患者档案（失败不阻断）
        if ("PATIENT".equals(userType)) {
            try {
                patientFeignClient.createPatient(
                        new CreatePatientDTO(user.getId(), user.getRealName(), user.getPhone()));
                log.info("[用户] 患者档案创建成功: userId={}", user.getId());
            } catch (Exception e) {
                log.warn("[用户] 患者档案创建失败（patient-service 不可用）: userId={}, error={}",
                        user.getId(), e.getMessage());
            }
        }

        // 6. 组装返回
        List<String> roles = roleMapper.findByUserId(user.getId()).stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setGender(user.getGender());
        vo.setUserType(user.getUserType());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoles(roles);
        return vo;
    }

    /**
     * 分页结果封装（records + total + 分页参数）
     */
    @Data
    @AllArgsConstructor
    public static class PageResult<T> {
        private List<T> records;
        private long total;
        private Integer pageNo;
        private Integer pageSize;
    }
}
