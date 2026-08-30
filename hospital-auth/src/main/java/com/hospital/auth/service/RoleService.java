package com.hospital.auth.service;

import com.hospital.auth.dto.AssignRoleDTO;
import com.hospital.auth.dto.CreateRoleDTO;
import com.hospital.auth.dto.UpdateRoleDTO;
import com.hospital.auth.entity.Role;
import com.hospital.auth.mapper.RoleMapper;
import com.hospital.auth.vo.RoleVO;
import com.hospital.common.constant.RoleConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务
 * <p>
 * 权限约束（四角色 RBAC）：
 * <ul>
 *   <li>角色的增删改、分配仅超级管理员可操作（{@link RoleConstant#SUPER_ADMIN}）；</li>
 *   <li>内置角色（超管/管理员/医生/患者）不可新增、编辑、删除；</li>
 *   <li>管理员不可操作管理员角色（不可新增/删除管理员角色、不可给用户分配管理员角色）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;

    /**
     * 查询全部角色（管理员及以上可查看）
     */
    public List<RoleVO> listAll() {
        return roleMapper.selectAll().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 查询单个角色（管理员及以上可查看）
     */
    public RoleVO getById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return toVO(role);
    }

    /**
     * 创建角色（仅超级管理员）
     * <p>
     * 禁止创建内置角色编码（超管/管理员/医生/患者），防止越权创建重复的预置角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO create(CreateRoleDTO dto) {
        // 内置角色编码禁止创建
        String roleCode = dto.getRoleCode() == null ? "" : dto.getRoleCode().trim();
        if (RoleConstant.isBuiltInRole(roleCode)) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "内置角色不可新增");
        }

        // 校验角色编码唯一性
        Role existing = roleMapper.selectByCode(roleCode);
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "角色编码已存在");
        }

        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setStatus(1);

        roleMapper.insert(role);
        log.info("[角色] 创建成功: roleCode={}", role.getRoleCode());
        return toVO(role);
    }

    /**
     * 更新角色（仅超级管理员）
     * <p>
     * 内置角色不可编辑（防止篡改超管/管理员/医生/患者角色的名称、状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO update(Long id, UpdateRoleDTO dto) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在");
        }
        if (RoleConstant.isBuiltInRole(role.getRoleCode())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "内置角色不可编辑");
        }

        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus());

        roleMapper.update(role);
        log.info("[角色] 更新成功: id={}", id);
        return toVO(roleMapper.selectById(id));
    }

    /**
     * 删除角色（仅超级管理员）
     * <p>
     * 内置角色不可删除（超管/管理员/医生/患者是系统运行基础）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在");
        }
        if (RoleConstant.isBuiltInRole(role.getRoleCode())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "内置角色不可删除");
        }
        // 清理关联表，防止孤儿记录
        roleMapper.deleteRolePermissionsByRoleId(id);
        roleMapper.deleteUserRolesByRoleId(id);
        roleMapper.deleteById(id);
        log.info("[角色] 删除成功: id={}", id);
    }

    /**
     * 为用户分配角色（仅超级管理员）
     * <p>
     * 先移除用户所有已有角色，再插入新的角色关联。
     * <p>
     * 安全约束：
     * <ul>
     *   <li>超级管理员角色（{@link RoleConstant#SUPER_ADMIN}）不可被分配或移除——
     *       它是唯一预置角色，若被分配会导致多超管，若被移除会导致系统失去最高权限；</li>
     *   <li>普通管理员角色（{@link RoleConstant#ADMIN}）的分配/移除也必须由超级管理员执行，
     *       管理员无权操作，避免提权/越权。</li>
     * </ul>
     * <p>
     * TODO: 存在 TOCTOU 竞态条件 —— 两个并发请求同时调用此方法时，各自先读取、再删除、再插入，
     *       后写入者会覆盖先写入者的结果，导致中间分配的 roles 丢失。
     *       生产环境建议引入分布式锁（如 Redisson）或使用 SELECT ... FOR UPDATE 悲观锁解决。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(AssignRoleDTO dto) {
        // 目标角色中禁止包含超级管理员角色（不可分配超管）
        List<Role> targetRoles = dto.getRoleIds().stream()
                .map(roleMapper::selectById)
                .collect(Collectors.toList());
        for (Role role : targetRoles) {
            if (role == null) {
                throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在: roleId=" + role);
            }
            if (RoleConstant.SUPER_ADMIN.equals(role.getRoleCode())) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "超级管理员角色不可分配");
            }
            if (role.getStatus() != 1) {
                throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在或已禁用: roleId=" + role.getId());
            }
        }

        // 禁止移除用户已有的超级管理员角色（保证唯一超管账号的关联不被破坏）
        List<Role> currentRoles = roleMapper.findByUserId(dto.getUserId());
        for (Role role : currentRoles) {
            if (RoleConstant.SUPER_ADMIN.equals(role.getRoleCode())) {
                boolean keepSuperAdmin = targetRoles.stream()
                        .anyMatch(r -> RoleConstant.SUPER_ADMIN.equals(r.getRoleCode()));
                if (!keepSuperAdmin) {
                    throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "不可移除用户的超级管理员角色");
                }
            }
        }

        // 先移除用户所有已有角色，再插入新角色
        for (Role role : currentRoles) {
            roleMapper.deleteUserRole(dto.getUserId(), role.getId());
        }
        for (Role role : targetRoles) {
            roleMapper.insertUserRole(dto.getUserId(), role.getId());
        }

        log.info("[角色] 分配成功: userId={}, roleIds={}", dto.getUserId(), dto.getRoleIds());
    }

    // ==================== 私有方法 ====================

    private RoleVO toVO(Role role) {
        return RoleVO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .status(role.getStatus())
                .createTime(role.getCreateTime())
                .build();
    }
}
