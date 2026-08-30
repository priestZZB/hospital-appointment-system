package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.InfusionOrder;
import com.hospital.medsupply.entity.InfusionRecord;
import com.hospital.medsupply.mapper.InfusionOrderMapper;
import com.hospital.medsupply.service.InfusionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 输液接口（医生开单 / 患者查单 / 护士站执行）
 */
@Slf4j
@RestController
@RequestMapping("/api/infusion")
@RequiredArgsConstructor
public class InfusionController {

    private final InfusionService infusionService;
    private final InfusionOrderMapper infusionOrderMapper;
    private final PatientFeignClient patientFeignClient;

    /** 医生/管理员开输液医嘱 */
    @AuditLog(value = "开输液医嘱", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_INFUSION_CREATE)
    @PostMapping("/orders")
    public Result<InfusionOrder> create(@RequestBody InfusionOrder order) {
        requireDoctorOrAdmin();
        return Result.ok(infusionService.createOrder(order));
    }

    /** 患者查本人输液单 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_INFUSION_QUERY)
    @GetMapping("/orders/my")
    public Result<Map<String, Object>> my(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) Long patientId) {
        requirePatient();
        // 简化：未传 patientId 时回退 userId（与 ExamReportController 一致）
        Long pid = patientId != null ? patientId : UserContext.getUserId();
        return Result.ok(infusionService.listByPatient(pid, pageNo, pageSize));
    }

    /** 输液单详情 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_INFUSION_QUERY)
    @GetMapping("/orders/{id}")
    public Result<InfusionOrder> detail(@PathVariable("id") Long id) {
        return Result.ok(infusionService.getById(id));
    }

    /** 患者未缴费输液单列表（患者本人 / 收费员 / 管理员） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_INFUSION_UNPAID)
    @GetMapping("/orders/unpaid")
    public Result<List<InfusionOrder>> unpaid(@RequestParam("patientId") Long patientId) {
        requirePatientSelfOrCashierOrAdmin(patientId);
        return Result.ok(infusionOrderMapper.selectUnpaidByPatient(patientId));
    }

    /** 护士站待执行列表 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_INFUSION_EXEC)
    @GetMapping("/nurse/pending")
    public Result<Map<String, Object>> pending(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        requireNurseOrAdmin();
        return Result.ok(infusionService.listPending(pageNo, pageSize));
    }

    /** 护士执行记录 */
    @AuditLog(value = "输液执行记录", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_INFUSION_EXEC)
    @PostMapping("/orders/{id}/records")
    public Result<InfusionRecord> record(@PathVariable("id") Long infusionOrderId,
                                         @RequestBody Map<String, Object> body) {
        requireNurseOrAdmin();
        return Result.ok(infusionService.executeRecord(
                infusionOrderId,
                (String) body.get("recordType"),
                (String) body.get("content"),
                (String) body.get("skinTestResult"),
                toInteger(body.get("dropRate")),
                UserContext.getUserId(),
                (String) body.get("operatorName")));
    }

    private void requireDoctorOrAdmin() {
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅医生或管理员可开输液医嘱");
        }
    }

    private void requirePatient() {
        if (!UserContext.isPatient()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅患者可查询本人输液单");
        }
    }

    private void requireNurseOrAdmin() {
        if (!UserContext.isNurseOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅护士或管理员可执行输液操作");
        }
    }

    /** 未缴费列表权限：收费员/管理员可查任意，患者仅可查本人 */
    private void requirePatientSelfOrCashierOrAdmin(Long patientId) {
        if (UserContext.isCashierOrAdmin()) {
            return;
        }
        if (patientId != null && patientId.equals(resolvePatientId())) {
            return;
        }
        throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅患者本人、收费员或管理员可查询");
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
            log.warn("[输液] 查询患者信息失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    private Integer toInteger(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
