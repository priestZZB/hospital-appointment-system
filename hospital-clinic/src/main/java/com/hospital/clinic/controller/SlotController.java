package com.hospital.clinic.controller;

import com.hospital.clinic.service.SlotService;
import com.hospital.clinic.vo.SlotVO;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 号源查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    /** 按科室+日期查询可用号源 */
    @GetMapping
    public Result<List<SlotVO>> listAvailable(
            @RequestParam Long departmentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(slotService.listAvailable(departmentId, date));
    }

    /** 按排班查询号源 */
    @GetMapping("/schedule/{scheduleId}")
    public Result<List<SlotVO>> listBySchedule(@PathVariable Long scheduleId) {
        return Result.ok(slotService.listBySchedule(scheduleId));
    }
}
