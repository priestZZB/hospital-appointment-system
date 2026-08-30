package com.hospital.auth.controller;

import com.hospital.auth.dto.AssignRoleDTO;
import com.hospital.auth.dto.CreateRoleDTO;
import com.hospital.auth.dto.UpdateRoleDTO;
import com.hospital.auth.service.RoleService;
import com.hospital.auth.vo.RoleVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口
 * <p>
 * 权限约束：
 * <ul>
 *   <li>查询角色：管理员及以上可查看；</li>
 *   <li>增删改、分配角色：仅超级管理员。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 查询全部角色（管理员及以上）
     */
    @RequiresPermission(PermissionConstant.AUTH_ROLE_QUERY)
    @GetMapping
    public Result<List<RoleVO>> list() {
        checkAdminOrSuperAdmin();
        return Result.ok(roleService.listAll());
    }

    /**
     * 查询单个角色（管理员及以上）
     */
    @RequiresPermission(PermissionConstant.AUTH_ROLE_QUERY)
    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        checkAdminOrSuperAdmin();
        return Result.ok(roleService.getById(id));
    }

    /**
     * 创建角色（仅超级管理员）
     */
    @AuditLog(value = "创建角色", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AUTH_ROLE_CREATE)
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody CreateRoleDTO dto) {
        checkSuperAdmin();
        return Result.ok(roleService.create(dto));
    }

    /**
     * 更新角色（仅超级管理员）
     */
    @AuditLog(value = "更新角色", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.AUTH_ROLE_UPDATE)
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleDTO dto) {
        checkSuperAdmin();
        return Result.ok(roleService.update(id, dto));
    }

    /**
     * 删除角色（仅超级管理员）
     */
    @AuditLog(value = "删除角色", operationType = "DELETE")
    @RequiresPermission(PermissionConstant.AUTH_ROLE_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkSuperAdmin();
        roleService.delete(id);
        return Result.ok();
    }

    /**
     * 为用户分配角色（仅超级管理员）
     */
    @AuditLog(value = "分配角色", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.AUTH_ROLE_ASSIGN)
    @PostMapping("/assign")
    public Result<Void> assign(@Valid @RequestBody AssignRoleDTO dto) {
        checkSuperAdmin();
        roleService.assignRole(dto);
        return Result.ok();
    }

    // ==================== 私有方法 ====================

    private void checkAdminOrSuperAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }

    private void checkSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅超级管理员可操作角色");
        }
    }
}
