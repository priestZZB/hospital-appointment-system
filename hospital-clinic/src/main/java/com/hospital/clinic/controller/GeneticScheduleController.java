package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.ScheduleSuggestionMapper;
import com.hospital.clinic.service.GeneticScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 遗传排班算法接口（迭代15 B1-1，base=/api/clinic/schedule-algo）。
 * 生成下一周排班建议（遗传算法）→ 管理员确认应用。
 */
@RestController
@RequestMapping("/api/clinic/schedule-algo")
@RequiredArgsConstructor
public class GeneticScheduleController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GeneticScheduleService geneticScheduleService;
    private final ScheduleSuggestionMapper suggestionMapper;

    /** 生成排班建议（默认下一周一为周起始） */
    @PostMapping("/generate")
    @AuditLog(value = "遗传算法排班生成", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.SCHEDULE_ALGO)
    public Result<Map<String, Object>> generate(
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "weekStart", required = false) String weekStart) {
        String ws = weekStart;
        if (ws == null || ws.isBlank()) {
            ws = LocalDate.now().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        try {
            LocalDate.parse(ws);
        } catch (Exception e) {
            ws = LocalDate.now().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return Result.ok(geneticScheduleService.generate(departmentId, ws));
    }

    /** 建议批次详情 */
    @GetMapping("/batch/{batchNo}")
    @RequiresPermission(PermissionConstant.SCHEDULE_ALGO)
    public Result<Map<String, Object>> batch(@PathVariable("batchNo") String batchNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchNo", batchNo);
        data.put("suggestions", suggestionMapper.selectByBatch(batchNo));
        return Result.ok(data);
    }

    /** 建议分页 */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.SCHEDULE_ALGO)
    public Result<Map<String, Object>> list(
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", suggestionMapper.selectPage(departmentId, status,
                (pageNo - 1) * pageSize, pageSize));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 应用建议批次 */
    @PostMapping("/batch/{batchNo}/apply")
    @AuditLog(value = "应用遗传排班建议", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.SCHEDULE_ALGO)
    public Result<Map<String, Object>> apply(@PathVariable("batchNo") String batchNo) {
        return Result.ok(geneticScheduleService.apply(batchNo));
    }

    private String rand() {
        return String.format("%03d", RANDOM.nextInt(1000));
    }
}
