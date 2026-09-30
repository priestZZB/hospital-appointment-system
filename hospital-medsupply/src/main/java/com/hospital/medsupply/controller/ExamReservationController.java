package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamReservation;
import com.hospital.medsupply.service.ExamReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 检查预约接口（D1，检查技师开预约/报到，医生可代开）
 * <p>
 * 影像类检查缴费后预约时段、到院报到；分页查询支持日期与状态筛选。
 */
@RestController
@RequestMapping("/api/admin/exam/reservation")
@RequiredArgsConstructor
public class ExamReservationController {

    private final ExamReservationService reservationService;

    /** 预约检查（body: applicationId / reserveDate(yyyy-MM-dd) / timeSlot / room） */
    @AuditLog(value = "检查预约", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAMRESV_BOOK)
    @PostMapping
    public Result<ExamReservation> book(@RequestBody Map<String, Object> body) {
        requireExamStaff();
        return Result.ok(reservationService.book(
                readLong(body, "applicationId"),
                readDate(body, "reserveDate"),
                readString(body, "timeSlot"),
                readString(body, "room"),
                UserContext.getUserId()));
    }

    /** 到院报到（BOOKED → CHECKED_IN，写报到时间） */
    @AuditLog(value = "检查报到", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAMRESV_CHECKIN)
    @PutMapping("/{id}/checkin")
    public Result<ExamReservation> checkin(@PathVariable("id") Long id) {
        requireExamStaff();
        return Result.ok(reservationService.checkin(id));
    }

    /** 预约状态变更（body: action = DONE-完成 / CANCELLED-取消） */
    @AuditLog(value = "检查预约状态变更", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAMRESV_CHECKIN)
    @PutMapping("/{id}/status")
    public Result<ExamReservation> updateStatus(@PathVariable("id") Long id,
                                                @RequestBody Map<String, String> body) {
        requireExamStaff();
        return Result.ok(reservationService.updateStatus(id, body != null ? body.get("action") : null));
    }

    /** 预约详情 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAMRESV_QUERY)
    @GetMapping("/{id}")
    public Result<ExamReservation> detail(@PathVariable("id") Long id) {
        return Result.ok(reservationService.getById(id));
    }

    /** 按检查申请查询预约记录 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAMRESV_QUERY)
    @GetMapping("/application/{applicationId}")
    public Result<List<ExamReservation>> byApplication(@PathVariable("applicationId") Long applicationId) {
        return Result.ok(reservationService.listByApplication(applicationId));
    }

    /** 预约分页（date=yyyy-MM-dd 可选，status 可选） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_EXAMRESV_QUERY)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        LocalDate reserveDate = null;
        if (date != null && !date.isBlank()) {
            try {
                reserveDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "date 格式应为 yyyy-MM-dd");
            }
        }
        return Result.ok(reservationService.listByPage(reserveDate, status, pageNo, pageSize));
    }

    /** 预约/报到操作限检查技师、医生或管理员 */
    private void requireExamStaff() {
        if (!UserContext.isExamTechOrAdmin() && !UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检查技师、医生或管理员可执行此操作");
        }
    }

    private Long readLong(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, key + " 应为数字");
        }
    }

    private LocalDate readDate(Map<String, Object> body, String key) {
        String value = readString(body, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, key + " 格式应为 yyyy-MM-dd");
        }
    }

    private String readString(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : value.toString();
    }
}
