package com.hospital.auth.controller;

import com.hospital.auth.dto.AssignRoleDTO;
import com.hospital.auth.dto.CreateRoleDTO;
import com.hospital.auth.dto.UpdateRoleDTO;
import com.hospital.auth.service.RoleService;
import com.hospital.auth.vo.RoleVO;
import com.hospital.common.annotation.AuditLog;
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
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 查询全部角色
     */
    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.listAll());
    }

    /**
     * 查询单个角色
     */
    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        return Result.ok(roleService.getById(id));
    }

    /**
     * 创建角色
     */
    @AuditLog(value = "创建角色", operationType = "INSERT")
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody CreateRoleDTO dto) {
        return Result.ok(roleService.create(dto));
    }

    /**
     * 更新角色
     */
    @AuditLog(value = "更新角色", operationType = "UPDATE")
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleDTO dto) {
        return Result.ok(roleService.update(id, dto));
    }

    /**
     * 删除角色
     */
    @AuditLog(value = "删除角色", operationType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    /**
     * 为用户分配角色
     */
    @AuditLog(value = "分配角色", operationType = "UPDATE")
    @PostMapping("/assign")
    public Result<Void> assign(@Valid @RequestBody AssignRoleDTO dto) {
        roleService.assignRole(dto);
        return Result.ok();
    }
}
