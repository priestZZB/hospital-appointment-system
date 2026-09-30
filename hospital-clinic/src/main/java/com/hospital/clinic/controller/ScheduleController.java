package com.hospital.clinic.controller;

import com.hospital.clinic.dto.ScheduleCreateDTO;
import com.hospital.clinic.service.ScheduleService;
import com.hospital.clinic.vo.ScheduleVO;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 提交排班申请（医生/科主任上报；门诊部管理员可直接提交并确认） */
    @AuditLog(value = "提交排班申请", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_CREATE)
    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleCreateDTO dto) {
        requireDoctorOrAdmin();
        return Result.ok(scheduleService.create(dto));
    }

    /** 门诊部确认排班（生成号源） */
    @AuditLog(value = "确认排班", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_CONFIRM)
    @PutMapping("/{id}/confirm")
    public Result<ScheduleVO> confirm(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(scheduleService.confirm(id));
    }

    /** 门诊部驳回排班 */
    @AuditLog(value = "驳回排班", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_REJECT)
    @PutMapping("/{id}/reject")
    public Result<ScheduleVO> reject(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(scheduleService.reject(id));
    }

    /** 待确认排班列表（门诊部确认用） */
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_CONFIRM)
    @GetMapping("/pending")
    public Result<List<ScheduleVO>> listPending(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        requireAdmin();
        long offset = (long) (Math.max(pageNo, 1) - 1) * Math.min(pageSize, 100);
        return Result.ok(scheduleService.listPending(offset, pageSize));
    }

    /** 排班日历视图 */
    @GetMapping
    public Result<List<ScheduleVO>> calendar(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(scheduleService.calendar(departmentId, startDate, endDate));
    }

    /**
     * 排班日历（扁平列表，迭代9 A8）
     * <p>
     * 按科室 + 起始日期 + 天数（默认 7 天）返回出诊计划扁平条目：
     * date/doctorId/doctorName/period/periodStart/periodEnd/confirmed/slotTotal/slotAvailable，
     * 前端可自行装配日期×医生矩阵。Spring 对字面量 /calendar 的匹配优先于 /{id}。
     */
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_CALENDAR)
    @GetMapping("/calendar")
    public Result<List<com.hospital.clinic.vo.ScheduleCalendarVO>> calendarFlat(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "days", defaultValue = "7") int days) {
        requireDoctorOrAdmin();
        return Result.ok(scheduleService.calendarFlat(departmentId, startDate, days));
    }

    /** 排班详情 */
    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.ok(scheduleService.getById(id));
    }

    /** 取消排班 */
    @AuditLog(value = "取消排班", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_SCHEDULE_CANCEL)
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        requireAdmin();
        scheduleService.cancel(id);
        return Result.ok();
    }

    private void requireAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅门诊部管理员可执行此操作");
        }
    }

    private void requireDoctorOrAdmin() {
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅医生/科主任/管理员可提交排班");
        }
    }
}
