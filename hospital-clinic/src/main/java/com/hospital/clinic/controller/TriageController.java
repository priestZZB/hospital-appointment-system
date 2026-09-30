package com.hospital.clinic.controller;

import com.hospital.clinic.dto.TriageSetPriorityDTO;
import com.hospital.clinic.service.TriageService;
import com.hospital.clinic.vo.TriageQueueVO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分诊台接口（迭代9 A1）
 * <p>
 * 分诊优先级设置（同步调整 Redis 叫号队列 score）与分诊台队列视图。
 * 操作角色：分诊护士/门诊护士/管理员（医生叫号仍走 {@code /api/clinic/call/**}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;

    /**
     * 分诊设置优先级
     * <p>
     * body: {checkinId, priority(0=急诊/1=优先/2=普通), returnFlag(0/1)}
     * 更新签到分诊字段并同步调整 Redis ZSet 的 score
     * （score = priorityRank × 1e13 + 原签到时间戳，回诊减 0.5 档插队）。
     */
    @AuditLog(value = "分诊设置优先级", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_TRIAGE_SET_PRIORITY)
    @PostMapping("/set-priority")
    public Result<TriageQueueVO> setPriority(@Valid @RequestBody TriageSetPriorityDTO dto) {
        checkTriageNurseOrAdmin();
        return Result.ok(triageService.setPriority(dto, UserContext.getUserId()));
    }

    /**
     * 分诊台队列视图
     * <p>
     * 科室 WAITING 患者按新 score 升序返回（与叫号 ZPOPMIN 顺序一致），
     * 含 priority/returnFlag/checkinTime/patientName。
     */
    @RequiresPermission(PermissionConstant.CLINIC_TRIAGE_QUEUE)
    @GetMapping("/queue")
    public Result<List<TriageQueueVO>> queue(@RequestParam("departmentId") Long departmentId) {
        checkTriageNurseOrAdmin();
        return Result.ok(triageService.triageQueue(departmentId));
    }

    private void checkTriageNurseOrAdmin() {
        if (!UserContext.isTriageNurseOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅分诊护士/护士/管理员可执行分诊操作");
        }
    }
}
