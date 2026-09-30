package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ResultItem;
import com.hospital.medsupply.service.ResultItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检验结果录入接口（检验技师/管理员，迭代8 C3）
 * <p>
 * 覆盖式录入报告结果明细（异常标志 ↑/↓ 由服务端判定）与结果查询。
 */
@RestController
@RequestMapping("/api/admin/lab/result")
@RequiredArgsConstructor
public class ResultItemController {

    private final ResultItemService resultItemService;

    /**
     * 覆盖式录入检验结果明细
     * <p>
     * body: {"items": [{"itemCode","itemName","resultValue","unit","refRange","sortOrder"}]}
     * 已审核发布（PUBLISHED）的报告拒绝重录。
     */
    @AuditLog(value = "检验结果录入", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_RESULT_ENTRY)
    @PostMapping("/report/{reportId}")
    public Result<List<ResultItem>> save(@PathVariable Long reportId,
                                         @RequestBody Map<String, Object> body) {
        requireLabTechOrAdmin();
        Object rawItems = body == null ? null : body.get("items");
        if (!(rawItems instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "items 检验结果明细不能为空");
        }
        List<ResultItem> items = new ArrayList<>();
        for (Object raw : rawList) {
            if (!(raw instanceof Map<?, ?> row)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "items 必须为对象数组");
            }
            items.add(toItem(row));
        }
        return Result.ok(resultItemService.saveReportResult(reportId, items, UserContext.getUserId()));
    }

    /** 按报告查询结果明细 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_RESULT_QUERY)
    @GetMapping("/report/{reportId}")
    public Result<List<ResultItem>> listByReport(@PathVariable Long reportId) {
        requireLabTechOrAdmin();
        return Result.ok(resultItemService.listByReport(reportId));
    }

    private void requireLabTechOrAdmin() {
        if (!UserContext.isLabTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检验技师或管理员可执行此操作");
        }
    }

    private static ResultItem toItem(Map<?, ?> row) {
        ResultItem item = new ResultItem();
        item.setItemCode(str(row.get("itemCode")));
        item.setItemName(str(row.get("itemName")));
        item.setResultValue(str(row.get("resultValue")));
        item.setUnit(str(row.get("unit")));
        item.setRefRange(str(row.get("refRange")));
        item.setSortOrder(intValue(row.get("sortOrder")));
        return item;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "sortOrder 必须为整数: " + value);
        }
    }
}
