package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.MedicalOrderCreateDTO;
import com.hospital.inpatient.service.MedicalOrderService;
import com.hospital.inpatient.vo.MedicalOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 住院医嘱接口：开立 -> 核对 -> 执行 -> 停止（状态机闭环）
 */
@RestController
@RequestMapping("/api/inpatient/order")
@RequiredArgsConstructor
public class MedicalOrderController {

    private final MedicalOrderService medicalOrderService;

    @AuditLog(value = "开立住院医嘱", operationType = "INPATIENT_ORDER_CREATE")
    @RequiresPermission(PermissionConstant.INPATIENT_ORDER_CREATE)
    @PostMapping
    public Result<MedicalOrderVO> create(@Valid @RequestBody MedicalOrderCreateDTO dto) {
        return Result.ok(medicalOrderService.create(UserContext.getUserId(), dto));
    }

    @AuditLog(value = "核对住院医嘱", operationType = "INPATIENT_ORDER_CONFIRM")
    @RequiresPermission(PermissionConstant.INPATIENT_ORDER_CONFIRM)
    @PostMapping("/{id}/confirm")
    public Result<MedicalOrderVO> confirm(@PathVariable("id") Long id) {
        return Result.ok(medicalOrderService.confirm(UserContext.getUserId(), id));
    }

    @AuditLog(value = "执行住院医嘱", operationType = "INPATIENT_ORDER_EXECUTE")
    @RequiresPermission(PermissionConstant.INPATIENT_ORDER_EXECUTE)
    @PostMapping("/{id}/execute")
    public Result<MedicalOrderVO> execute(@PathVariable("id") Long id,
                                          @RequestParam(value = "result", required = false) String result) {
        return Result.ok(medicalOrderService.execute(UserContext.getUserId(), id, result));
    }

    @AuditLog(value = "停止住院医嘱", operationType = "INPATIENT_ORDER_STOP")
    @RequiresPermission(PermissionConstant.INPATIENT_ORDER_STOP)
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable("id") Long id) {
        medicalOrderService.stop(UserContext.getUserId(), id);
        return Result.ok();
    }

    @RequiresPermission(PermissionConstant.INPATIENT_ORDER_QUERY)
    @GetMapping
    public Result<List<MedicalOrderVO>> list(
            @RequestParam(value = "admissionId") Long admissionId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(medicalOrderService.listOrders(admissionId, status));
    }
}
