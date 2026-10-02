package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.AttendanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 考勤打卡接口（迭代14 L4，base=/api/clinic/attendances）。
 * 每人每天一次上班打卡（重复打卡拒绝）+ 签退；支持按日期/人员查询与近 30 天统计。
 */
@RestController
@RequestMapping("/api/clinic/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceMapper attendanceMapper;

    /** 上班打卡（当前用户） */
    @PostMapping("/checkin")
    @AuditLog(value = "考勤上班打卡", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.ATTENDANCE_MANAGE)
    public Result<String> checkin() {
        String workDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (attendanceMapper.selectByUserDate(UserContext.getUserId(), workDate) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "今日已打卡，请勿重复操作");
        }
        attendanceMapper.checkin(UserContext.getUserId(), "user-" + UserContext.getUserId(), workDate, null);
        return Result.ok("打卡成功");
    }

    /** 下班签退（当前用户） */
    @PostMapping("/checkout")
    @AuditLog(value = "考勤下班签退", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.ATTENDANCE_MANAGE)
    public Result<String> checkout() {
        String workDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (attendanceMapper.checkout(UserContext.getUserId(), workDate) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "今日未上班打卡或已签退");
        }
        return Result.ok("签退成功");
    }

    /** 我的今日考勤 */
    @GetMapping("/today")
    @RequiresPermission(PermissionConstant.ATTENDANCE_MANAGE)
    public Result<Map<String, Object>> today() {
        String workDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return Result.ok(attendanceMapper.selectByUserDate(UserContext.getUserId(), workDate));
    }

    /** 考勤分页（userId/date） */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.ATTENDANCE_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(value = "userId", required = false) Long userId,
                                            @RequestParam(value = "date", required = false) String date,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", attendanceMapper.selectPage(userId, date, (pageNo - 1) * pageSize, pageSize));
        page.put("total", attendanceMapper.countPage(userId, date));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 近 30 天出勤统计 */
    @GetMapping("/summary")
    @RequiresPermission(PermissionConstant.ATTENDANCE_MANAGE)
    public Result<List<Map<String, Object>>> summary() {
        return Result.ok(attendanceMapper.summary());
    }
}
