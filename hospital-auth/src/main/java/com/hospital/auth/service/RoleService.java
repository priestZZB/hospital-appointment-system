package com.hospital.auth.service;

import com.hospital.auth.dto.AssignRoleDTO;
import com.hospital.auth.dto.CreateRoleDTO;
import com.hospital.auth.dto.UpdateRoleDTO;
import com.hospital.auth.entity.Role;
import com.hospital.auth.mapper.RoleMapper;
import com.hospital.auth.vo.RoleVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;

    /**
     * 查询全部角色
     */
    public List<RoleVO> listAll() {
        return roleMapper.selectAll().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 查询单个角色
     */
    public RoleVO getById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return toVO(role);
    }

    /**
     * 创建角色
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO create(CreateRoleDTO dto) {
        // 校验角色编码唯一性
        Role existing = roleMapper.selectByCode(dto.getRoleCode());
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "角色编码已存在");
        }

        Role role = new Role();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setStatus(1);

        roleMapper.insert(role);
        log.info("[角色] 创建成功: roleCode={}", role.getRoleCode());
        return toVO(role);
    }

    /**
     * 更新角色
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO update(Long id, UpdateRoleDTO dto) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在");
        }

        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus());

        roleMapper.update(role);
        log.info("[角色] 更新成功: id={}", id);
        return toVO(roleMapper.selectById(id));
    }

    /**
     * 删除角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在");
        }
        // 清理关联表，防止孤儿记录
        roleMapper.deleteRolePermissionsByRoleId(id);
        roleMapper.deleteUserRolesByRoleId(id);
        roleMapper.deleteById(id);
        log.info("[角色] 删除成功: id={}", id);
    }

    /**
     * 为用户分配角色
     * <p>
     * 先移除用户所有已有角色，再插入新的角色关联。
     * <p>
     * TODO: 存在 TOCTOU 竞态条件 —— 两个并发请求同时调用此方法时，各自先读取、再删除、再插入，
     *       后写入者会覆盖先写入者的结果，导致中间分配的 roles 丢失。
     *       生产环境建议引入分布式锁（如 Redisson）或使用 SELECT ... FOR UPDATE 悲观锁解决。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(AssignRoleDTO dto) {
        List<Role> currentRoles = roleMapper.findByUserId(dto.getUserId());
        for (Role role : currentRoles) {
            roleMapper.deleteUserRole(dto.getUserId(), role.getId());
        }

        for (Long roleId : dto.getRoleIds()) {
            Role role = roleMapper.selectById(roleId);
            if (role == null || role.getStatus() != 1) {
                throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "角色不存在或已禁用: roleId=" + roleId);
            }
            roleMapper.insertUserRole(dto.getUserId(), roleId);
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
