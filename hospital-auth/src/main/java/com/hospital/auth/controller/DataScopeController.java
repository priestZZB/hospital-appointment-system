package com.hospital.auth.controller;

import com.hospital.auth.dto.DataScopeApplyDTO;
import com.hospital.auth.dto.DataScopeApproveDTO;
import com.hospital.auth.dto.DataScopeApplyQueryDTO;
import com.hospital.auth.service.DataScopeService;
import com.hospital.auth.service.UserService;
import com.hospital.auth.vo.DataScopeApplyVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 跨科室数据范围申请接口（迭代 5 阶段 3）
 * <p>
 * 默认数据范围见 {@link DataScopeService}；跨科室需申请 → 科主任/管理员/超管审批。
 */
@RestController
@RequestMapping("/api/auth/data-scope")
@RequiredArgsConstructor
public class DataScopeController {

    private final DataScopeService dataScopeService;

    /**
     * 提交跨科室申请（业务角色）
     */
    @AuditLog(value = "提交跨科室申请", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AUTH_DATA_SCOPE_APPLY)
    @PostMapping("/apply")
    public Result<DataScopeApplyVO> apply(@Valid @RequestBody DataScopeApplyDTO dto) {
        return Result.ok(dataScopeService.apply(dto));
    }

    /**
     * 审批跨科室申请（科主任/管理员/超管）
     */
    @AuditLog(value = "审批跨科室申请", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.AUTH_DATA_SCOPE_APPROVE)
    @PutMapping("/{applyId}/approve")
    public Result<DataScopeApplyVO> approve(@PathVariable Long applyId,
                                            @Valid @RequestBody DataScopeApproveDTO dto) {
        return Result.ok(dataScopeService.approve(applyId, dto));
    }

    /**
     * 我的跨科室申请列表（全部登录用户）
     */
    @RequiresPermission(PermissionConstant.AUTH_DATA_SCOPE_LIST)
    @GetMapping("/my")
    public Result<UserService.PageResult<DataScopeApplyVO>> my(@ModelAttribute DataScopeApplyQueryDTO dto) {
        dto.setUserId(com.hospital.common.interceptor.UserContext.getUserId());
        return Result.ok(dataScopeService.pageQuery(dto));
    }

    /**
     * 待审批列表（科主任/管理员/超管；传 status=PENDING）
     */
    @RequiresPermission(PermissionConstant.AUTH_DATA_SCOPE_APPROVE)
    @GetMapping("/pending")
    public Result<UserService.PageResult<DataScopeApplyVO>> pending(@ModelAttribute DataScopeApplyQueryDTO dto) {
        dto.setStatus("PENDING");
        dto.setUserId(null);
        return Result.ok(dataScopeService.pageQuery(dto));
    }
}