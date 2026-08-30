package com.hospital.auth.controller;

import com.hospital.auth.dto.AssignPositionDTO;
import com.hospital.auth.dto.PositionPageQueryDTO;
import com.hospital.auth.dto.PositionSaveDTO;
import com.hospital.auth.entity.Position;
import com.hospital.auth.service.PositionService;
import com.hospital.auth.service.UserService;
import com.hospital.auth.vo.PositionVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位管理接口（迭代 5 阶段 2）
 * <p>
 * 岗位 = 部门 + 职务，纯展示、不参与权限。
 * 权限：超管可增删改查 + 分配；管理员可查看 + 给非管理员分配。
 */
@RestController
@RequestMapping("/api/auth/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    /**
     * 分页查询岗位（超管/管理员）
     */
    @RequiresPermission(PermissionConstant.AUTH_POSITION_LIST)
    @GetMapping
    public Result<UserService.PageResult<PositionVO>> page(@ModelAttribute PositionPageQueryDTO dto) {
        return Result.ok(positionService.pageQuery(dto));
    }

    /**
     * 查询全部启用岗位（分配岗位下拉；超管/管理员）
     */
    @RequiresPermission(PermissionConstant.AUTH_POSITION_LIST)
    @GetMapping("/enabled")
    public Result<List<Position>> enabled() {
        return Result.ok(positionService.listEnabled());
    }

    /**
     * 岗位详情（超管/管理员）
     */
    @RequiresPermission(PermissionConstant.AUTH_POSITION_LIST)
    @GetMapping("/{id}")
    public Result<PositionVO> detail(@PathVariable Long id) {
        return Result.ok(positionService.detail(id));
    }

    /**
     * 创建岗位（仅超管）
     */
    @AuditLog(value = "创建岗位", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AUTH_POSITION_CREATE)
    @PostMapping
    public Result<PositionVO> create(@Valid @RequestBody PositionSaveDTO dto) {
        checkSuperAdmin();
        return Result.ok(positionService.create(dto));
    }

    /**
     * 更新岗位（仅超管）
     */
    @AuditLog(value = "更新岗位", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.AUTH_POSITION_UPDATE)
    @PutMapping("/{id}")
    public Result<PositionVO> update(@PathVariable Long id, @Valid @RequestBody PositionSaveDTO dto) {
        checkSuperAdmin();
        return Result.ok(positionService.update(id, dto));
    }

    /**
     * 删除岗位（仅超管）
     */
    @AuditLog(value = "删除岗位", operationType = "DELETE")
    @RequiresPermission(PermissionConstant.AUTH_POSITION_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkSuperAdmin();
        positionService.delete(id);
        return Result.ok();
    }

    /**
     * 给用户分配/撤销岗位（超管不限；管理员仅可给非管理员分配）
     */
    @AuditLog(value = "分配岗位", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.AUTH_POSITION_ASSIGN)
    @PostMapping("/assign")
    public Result<Void> assign(@Valid @RequestBody AssignPositionDTO dto) {
        positionService.assign(dto);
        return Result.ok();
    }

    private void checkSuperAdmin() {
        if (!com.hospital.common.interceptor.UserContext.isSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅超级管理员可操作岗位");
        }
    }
}