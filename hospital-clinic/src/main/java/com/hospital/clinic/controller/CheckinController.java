package com.hospital.clinic.controller;

import com.hospital.clinic.dto.CheckinDTO;
import com.hospital.clinic.service.CheckinService;
import com.hospital.clinic.service.TriageService;
import com.hospital.clinic.vo.CheckinVO;
import com.hospital.clinic.vo.QueueSnapshotVO;
import com.hospital.clinic.vo.QueueStatusVO;
import com.hospital.clinic.vo.TriageQueueVO;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;
    private final TriageService triageService;

    /** 患者签到 */
    @AuditLog(value = "患者签到", operationType = "CHECKIN")
    @PostMapping("/checkin")
    public Result<CheckinVO> checkin(@Valid @RequestBody CheckinDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(checkinService.checkin(userId, dto));
    }

    /** 查询排队状态 */
    @GetMapping("/checkin/{checkinId}/queue-status")
    public Result<QueueStatusVO> queueStatus(@PathVariable("checkinId") Long checkinId) {
        return Result.ok(checkinService.getQueueStatus(checkinId));
    }

    /**
     * 检查/检验完成回诊（迭代9 A6）
     * <p>
     * 已叫号/就诊中的患者完成检查检验后重新排队：置 WAITING + return_flag=1，
     * Redis 队列按回诊插队 score 重新入队（同档普通患者之前）。
     * 允许医生（本 Department 校验在叫号侧）/护士/管理员触发。
     */
    @AuditLog(value = "检查检验完成回诊", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_REVISIT_MARK)
    @PostMapping("/checkin/{checkinId}/rejoin")
    public Result<TriageQueueVO> rejoin(@PathVariable("checkinId") Long checkinId) {
        if (!UserContext.isDoctorOrAdmin() && !UserContext.isTriageNurseOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅医生/护士/管理员可执行回诊操作");
        }
        return Result.ok(triageService.rejoin(checkinId));
    }

    /** 科室排队快照（签到叫号大屏：当前叫号 + 等待列表，医生/管理员） */
    @RequiresPermission(PermissionConstant.CLINIC_CHECKIN_SNAPSHOT)
    @GetMapping("/checkin/queue")
    public Result<QueueSnapshotVO> queueSnapshot(@RequestParam("departmentId") Long departmentId) {
        checkDoctorOrAdmin();
        return Result.ok(checkinService.queueSnapshot(departmentId));
    }

    private void checkDoctorOrAdmin() {
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }
}
