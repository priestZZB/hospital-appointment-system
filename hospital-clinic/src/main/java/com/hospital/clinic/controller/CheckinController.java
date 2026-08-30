package com.hospital.clinic.controller;

import com.hospital.clinic.dto.CheckinDTO;
import com.hospital.clinic.service.CheckinService;
import com.hospital.clinic.vo.CheckinVO;
import com.hospital.clinic.vo.QueueSnapshotVO;
import com.hospital.clinic.vo.QueueStatusVO;
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
