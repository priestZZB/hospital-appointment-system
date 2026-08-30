package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/exam")
@RequiredArgsConstructor
public class ExamItemController {

    private final ExamService examService;

    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAM_ITEM_QUERY)
    @GetMapping("/item")
    public Result<List<ExamItem>> list(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String itemType) {
        requireAdmin();
        return Result.ok(examService.itemList(keyword, itemType));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAM_ITEM_CREATE)
    @PostMapping("/item")
    @AuditLog(value = "创建检查项目", operationType = "INSERT")
    public Result<ExamItem> create(@RequestBody ExamItem item) {
        requireAdmin();
        return Result.ok(examService.createItem(item));
    }

    private void requireAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可执行此操作");
        }
    }
}
