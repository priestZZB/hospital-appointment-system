package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.DecoctionOrder;
import com.hospital.medsupply.service.DecoctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 中药代煎订单接口
 * <p>
 * 下单（患者/医生，处方为 HERBAL 时）；状态流转（药师）；
 * 患者本人订单查询（经 PatientFeignClient 解析本人 patientId）与药师分页。
 */
@Slf4j
@RestController
@RequestMapping("/api/medsupply/decoction")
@RequiredArgsConstructor
public class DecoctionController {

    private final DecoctionService decoctionService;
    private final PatientFeignClient patientFeignClient;

    /** 代煎下单（患者/医生；body 未带 patientId 时解析本人档案） */
    @AuditLog(value = "代煎下单", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DECOCTION_CREATE)
    @PostMapping
    public Result<DecoctionOrder> create(@RequestBody DecoctionOrder order) {
        if (order.getPatientId() == null) {
            Long myPatientId = resolvePatientId();
            if (myPatientId == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "patientId 不能为空");
            }
            order.setPatientId(myPatientId);
        }
        return Result.ok(decoctionService.create(order));
    }

    /** 状态流转（药师；body{action}：DECOCTING/READY/DISPENSED/CANCELLED） */
    @AuditLog(value = "代煎状态流转", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DECOCTION_MANAGE)
    @PutMapping("/{id}/handle")
    public Result<DecoctionOrder> handle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可执行此操作");
        }
        return Result.ok(decoctionService.handle(id, body.get("action")));
    }

    /** 患者本人订单分页 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DECOCTION_QUERY)
    @GetMapping("/my")
    public Result<Map<String, Object>> my(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Long patientId = resolvePatientId();
        if (patientId == null) {
            // 未实名/患者档案缺失：返回空分页
            return Result.ok(Collections.emptyMap());
        }
        return Result.ok(decoctionService.my(patientId, pageNo, pageSize));
    }

    /** 药师订单分页（按状态筛选） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_DECOCTION_QUERY)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可查询全部代煎订单");
        }
        return Result.ok(decoctionService.page(status, pageNo, pageSize));
    }

    /** 通过 userId 解析 patientId（auth userId ≠ patient db id） */
    private Long resolvePatientId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.get("id") == null) {
                return null;
            }
            Object id = patientInfo.get("id");
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
            return Long.parseLong(id.toString());
        } catch (Exception e) {
            log.warn("[代煎] 查询患者信息失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }
}
