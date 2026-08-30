package com.hospital.clinic.controller;

import com.hospital.clinic.dto.CallNextDTO;
import com.hospital.clinic.service.CallService;
import com.hospital.clinic.vo.CallMessageVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 叫号接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    /** 医生叫号（下一号） */
    @AuditLog(value = "医生叫号", operationType = "CALL_NEXT")
    @RequiresPermission(PermissionConstant.CLINIC_CALL_NEXT)
    @PostMapping("/call/next")
    public Result<CallMessageVO> callNext(@Valid @RequestBody CallNextDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(callService.callNext(dto.getDepartmentId(), dto.getConsultRoom(), userId));
    }

    /** 重呼 */
    @RequiresPermission(PermissionConstant.CLINIC_CALL_RECALL)
    @PostMapping("/call/{checkinId}/recall")
    public Result<CallMessageVO> recall(@PathVariable("checkinId") Long checkinId,
                                        @RequestParam("consultRoom") String consultRoom) {
        return Result.ok(callService.recall(checkinId, consultRoom, UserContext.getUserId()));
    }

    /** 过号 */
    @RequiresPermission(PermissionConstant.CLINIC_CALL_MISSED)
    @PutMapping("/call/{checkinId}/missed")
    public Result<Void> markMissed(@PathVariable("checkinId") Long checkinId) {
        callService.markMissed(checkinId, UserContext.getUserId());
        return Result.ok();
    }
}
