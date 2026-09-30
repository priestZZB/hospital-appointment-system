package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.Specimen;
import com.hospital.medsupply.service.SpecimenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 标本采集管理接口（检验技师/管理员，迭代8 C2）
 * <p>
 * 采集登记、核收/拒收、送检流转与标本查询。
 */
@RestController
@RequestMapping("/api/admin/lab/specimen")
@RequiredArgsConstructor
public class SpecimenController {

    private final SpecimenService specimenService;

    /** 标本采集登记（校验 LAB 申请 + 已缴费 + 状态允许，生成标本号） */
    @AuditLog(value = "标本采集", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_SPECIMEN_COLLECT)
    @PostMapping("/collect")
    public Result<Specimen> collect(@RequestBody Map<String, Object> body) {
        requireLabTechOrAdmin();
        return Result.ok(specimenService.collect(
                toLong(body.get("applicationId")),
                toStr(body.get("specimenType")),
                toStr(body.get("container")),
                toStr(body.get("collectSite")),
                UserContext.getUserId()));
    }

    /** 标本核收/拒收（accept=false 时 remark 拒收原因必填） */
    @AuditLog(value = "标本核收", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_SPECIMEN_RECEIVE)
    @PutMapping("/{id}/receive")
    public Result<Specimen> receive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireLabTechOrAdmin();
        return Result.ok(specimenService.receive(id, toBool(body.get("accept")),
                toStr(body.get("remark")), UserContext.getUserId()));
    }

    /** 标本送检流转（RECEIVED → TESTING） */
    @AuditLog(value = "标本送检", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_SPECIMEN_RECEIVE)
    @PutMapping("/{id}/testing")
    public Result<Specimen> testing(@PathVariable Long id) {
        requireLabTechOrAdmin();
        return Result.ok(specimenService.markTesting(id));
    }

    /** 标本详情 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_SPECIMEN_QUERY)
    @GetMapping("/{id}")
    public Result<Specimen> detail(@PathVariable Long id) {
        requireLabTechOrAdmin();
        return Result.ok(specimenService.getById(id));
    }

    /** 标本分页（支持状态/标本类型筛选） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_SPECIMEN_QUERY)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "specimenType", required = false) String specimenType,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        requireLabTechOrAdmin();
        return Result.ok(specimenService.listByPage(status, specimenType, pageNo, pageSize));
    }

    private void requireLabTechOrAdmin() {
        if (!UserContext.isLabTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检验技师或管理员可执行此操作");
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "数值字段格式错误: " + value);
        }
    }

    private static String toStr(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }
}
