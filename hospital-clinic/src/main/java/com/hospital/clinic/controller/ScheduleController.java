package com.hospital.clinic.controller;

import com.hospital.clinic.dto.ScheduleCreateDTO;
import com.hospital.clinic.service.ScheduleService;
import com.hospital.clinic.vo.ScheduleVO;
import com.hospital.common.annotation.AuditLog;
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

    /** 创建排班 */
    @AuditLog(value = "创建排班", operationType = "INSERT")
    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleCreateDTO dto) {
        return Result.ok(scheduleService.create(dto));
    }

    /** 排班日历视图 */
    @GetMapping
    public Result<List<ScheduleVO>> calendar(
            @RequestParam Long departmentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.ok(scheduleService.calendar(departmentId, startDate, endDate));
    }

    /** 排班详情 */
    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.ok(scheduleService.getById(id));
    }

    /** 取消排班 */
    @AuditLog(value = "取消排班", operationType = "UPDATE")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        scheduleService.cancel(id);
        return Result.ok();
    }
}
