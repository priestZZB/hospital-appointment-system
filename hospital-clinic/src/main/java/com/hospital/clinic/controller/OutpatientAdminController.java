package com.hospital.clinic.controller;

import com.hospital.clinic.service.ScheduleAutoGenerateService;
import com.hospital.clinic.service.ScheduleService;
import com.hospital.clinic.service.SlotService;
import com.hospital.clinic.vo.ScheduleVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 门诊管理端接口（迭代9 A2/A4/A5/A8）
 * <p>
 * 号源绿色通道设置、排班加号开关、号别（分层定价）设置、自动排班手动触发。
 * 全部为门诊部管理员操作，路径统一挂在 /api/admin/** 下。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class OutpatientAdminController {

    private final SlotService slotService;
    private final ScheduleService scheduleService;
    private final ScheduleAutoGenerateService scheduleAutoGenerateService;

    /**
     * 设置单个号源通道类型（绿色通道 A4）
     *
     * @param channelType NORMAL-普通 / GREEN-绿色通道（老年/军人/急诊优先）
     */
    @AuditLog(value = "号源通道设置", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_GREENCHANNEL_MANAGE)
    @PutMapping("/slot/{id}/channel")
    public Result<?> setSlotChannel(@PathVariable Long id,
                                    @RequestParam("channelType") String channelType) {
        checkAdmin();
        return Result.ok(slotService.setChannelType(id, channelType));
    }

    /**
     * 批量设置排班号源通道（绿色通道 A4）
     * <p>
     * 将排班下按号序最靠前的 count 个可用号源设为目标通道，返回实际改动数。
     */
    @AuditLog(value = "排班绿色通道批量设置", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_GREENCHANNEL_MANAGE)
    @PostMapping("/schedule/{scheduleId}/green-slots")
    public Result<Map<String, Object>> setScheduleGreenSlots(
            @PathVariable Long scheduleId,
            @RequestParam(value = "channelType", defaultValue = "GREEN") String channelType,
            @RequestParam(value = "count", defaultValue = "3") int count) {
        checkAdmin();
        int changed = slotService.setChannelTypeFirstN(scheduleId, channelType, count);
        Map<String, Object> result = new HashMap<>();
        result.put("scheduleId", scheduleId);
        result.put("channelType", channelType);
        result.put("requested", count);
        result.put("changed", changed);
        return Result.ok(result);
    }

    /**
     * 设置排班加号开关（A2）
     *
     * @param overbook 0-禁止 / 1-允许加号超挂
     */
    @AuditLog(value = "排班加号开关设置", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_OVERBOOK_MANAGE)
    @PutMapping("/schedule/{id}/overbook")
    public Result<ScheduleVO> setScheduleOverbook(@PathVariable Long id,
                                                  @RequestParam("overbook") Integer overbook) {
        checkAdmin();
        return Result.ok(scheduleService.setOverbook(id, overbook));
    }

    /**
     * 设置排班号别（分层定价 A5）
     *
     * @param feeType NORMAL-普通号 / EXPERT-专家号（按医生职称档位价：CHIEF 50 / 副主任 40 / ATTENDING 30 / RESIDENT 20）
     */
    @AuditLog(value = "排班号别设置", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_PRICEDETAIL_MANAGE)
    @PutMapping("/schedule/{id}/fee-type")
    public Result<ScheduleVO> setScheduleFeeType(@PathVariable Long id,
                                                 @RequestParam("feeType") String feeType) {
        checkAdmin();
        return Result.ok(scheduleService.setFeeType(id, feeType));
    }

    /**
     * 自动排班手动触发（A8）
     * <p>
     * 为指定日期按默认出诊模板生成排班并确认生成号源（幂等，可重复执行），
     * 执行结果写 schedule_generate_log，便于测试与补跑。
     */
    @AuditLog(value = "自动排班手动触发", operationType = "OTHER")
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_GENERATE)
    @PostMapping("/schedule/generate")
    public Result<Map<String, Object>> generate(
            @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        checkAdmin();
        var logEntity = scheduleAutoGenerateService.generateForDate(date);
        Map<String, Object> result = new HashMap<>();
        result.put("bizDate", logEntity.getBizDate());
        result.put("createdCount", logEntity.getCreatedCount());
        result.put("message", logEntity.getMessage());
        return Result.ok(result);
    }

    private void checkAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅门诊部管理员可执行此操作");
        }
    }
}
