package com.hospital.clinic.controller;

import com.hospital.clinic.dto.AppointmentSubmitDTO;
import com.hospital.clinic.dto.AppointmentPageQueryDTO;
import com.hospital.clinic.dto.RescheduleDTO;
import com.hospital.clinic.service.AppointmentService;
import com.hospital.clinic.vo.AppointmentVO;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预约挂号接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * 挂号下单
     * <p>
     * 患者需完成实名认证。使用分布式锁 + 乐观锁保证高并发下不超卖。
     */
    @AuditLog(value = "挂号下单", operationType = "INSERT")
    @PostMapping
    public Result<AppointmentVO> submit(@Valid @RequestBody AppointmentSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        log.info("[挂号] 患者挂号请求: userId={}, slotId={}", userId, dto.getSlotId());
        return Result.ok(appointmentService.submit(userId, dto));
    }

    /** 我的预约列表 */
    @GetMapping
    public Result<List<AppointmentVO>> list() {
        Long userId = UserContext.getUserId();
        return Result.ok(appointmentService.listByPatient(userId));
    }

    /** 管理端预约分页列表（多条件筛选，仅管理员） */
    @RequiresPermission(PermissionConstant.CLINIC_APPOINTMENT_PAGE)
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@ModelAttribute AppointmentPageQueryDTO dto) {
        checkAdmin();
        return Result.ok(appointmentService.pageForAdmin(dto));
    }

    /** 预约详情 */
    @GetMapping("/{id}")
    public Result<AppointmentVO> getById(@PathVariable Long id) {
        return Result.ok(appointmentService.getById(id, UserContext.getUserId()));
    }

    /** 取消预约 */
    @AuditLog(value = "取消预约", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_APPOINTMENT_CANCEL)
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(value = "reason", required = false) String reason) {
        appointmentService.cancel(id, reason != null ? reason : "患者取消预约");
        return Result.ok();
    }

    /**
     * 医生加号（迭代9 A2）
     * <p>
     * 号源已约满（BOOKED）且排班开启加号（schedule.overbook=1）时，
     * 在同名源上追加挂号，生成 overbook_flag=1 的预约与支付订单。
     * 请求体与既有挂号接口一致（slotId + scheduleId）。
     */
    @AuditLog(value = "医生加号", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.CLINIC_OVERBOOK_CREATE)
    @PostMapping("/overbook")
    public Result<AppointmentVO> overbook(@Valid @RequestBody AppointmentSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        log.info("[加号] 加号请求: userId={}, slotId={}", userId, dto.getSlotId());
        return Result.ok(appointmentService.overbook(userId, dto));
    }

    /**
     * 退号改期（迭代9 A3）
     * <p>
     * 未就诊/未取消的预约改期到同科室另一号源：新号源扣减、旧号源释放、
     * 预约号源信息更新；原支付状态不变（不重复收费）。
     * 患者仅可改本人预约，管理员/医生可代办。
     */
    @AuditLog(value = "退号改期", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_RESCHEDULE_APPLY)
    @PostMapping("/{id}/reschedule")
    public Result<AppointmentVO> reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleDTO dto) {
        Long userId = UserContext.getUserId();
        log.info("[改期] 改期请求: userId={}, appointmentId={}, newSlotId={}", userId, id, dto.getNewSlotId());
        return Result.ok(appointmentService.reschedule(id, userId, dto));
    }

    private void checkAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }
}
