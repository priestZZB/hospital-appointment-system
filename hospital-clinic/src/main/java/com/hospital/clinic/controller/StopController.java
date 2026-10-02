package com.hospital.clinic.controller;

import com.hospital.clinic.dto.StopApproveDTO;
import com.hospital.clinic.service.StopService;
import com.hospital.clinic.vo.StopApplicationVO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 停诊管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class StopController {

    private final StopService stopService;

    /** 医生发起停诊申请 */
    @AuditLog(value = "停诊申请", operationType = "STOP_APPLY")
    @PostMapping("/stop/apply")
    public Result<StopApplicationVO> apply(@Valid @RequestBody com.hospital.clinic.dto.StopApplicationDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(stopService.apply(userId, dto.getScheduleId(), dto.getApplyReason()));
    }

    /** 科主任初审停诊申请（通过 → 待门诊部终审；驳回 → 驳回） */
    @AuditLog(value = "停诊科主任初审", operationType = "STOP_CHIEF_REVIEW")
    @RequiresPermission(PermissionConstant.CLINIC_STOP_CHIEF_REVIEW)
    @PutMapping("/stop/{applicationId}/chief-review")
    public Result<StopApplicationVO> chiefReview(@PathVariable("applicationId") Long applicationId,
                                                 @Valid @RequestBody StopApproveDTO dto) {
        Long userId = UserContext.getUserId();
        if (!UserContext.isDeptChiefOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅科主任或管理员可初审停诊");
        }
        return Result.ok(stopService.chiefReview(applicationId, userId, dto.getAction(), dto.getApproveComment()));
    }

    /** 门诊部终审停诊申请 */
    @AuditLog(value = "停诊终审", operationType = "STOP_APPROVE")
    @RequiresPermission(PermissionConstant.CLINIC_STOP_APPROVE)
    @PutMapping("/stop/{applicationId}/approve")
    public Result<StopApplicationVO> approve(@PathVariable("applicationId") Long applicationId,
                                              @Valid @RequestBody StopApproveDTO dto) {
        Long userId = UserContext.getUserId();
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅门诊部管理员可终审停诊");
        }
        return Result.ok(stopService.approve(applicationId, userId, dto.getAction(), dto.getApproveComment()));
    }

    /** 停诊申请列表（按状态筛选） */
    @GetMapping("/stop/list")
    public Result<List<StopApplicationVO>> listByStatus(@RequestParam(value = "status", required = false) String status,
                                                         @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        long offset = (long) (Math.max(pageNo, 1) - 1) * size;
        return Result.ok(stopService.listByStatus(status, offset, size));
    }

    /** 某医生的停诊申请列表 */
    @GetMapping("/stop/doctor/{doctorId}")
    public Result<List<StopApplicationVO>> listByDoctor(@PathVariable("doctorId") Long doctorId,
                                                         @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        long offset = (long) (Math.max(pageNo, 1) - 1) * size;
        return Result.ok(stopService.listByDoctor(doctorId, offset, size));
    }
}
