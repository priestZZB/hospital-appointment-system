package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.EmergencyTriageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 急诊预检分级接口（迭代14 G1，base=/api/clinic/emergency-triages）。
 * 分诊台录入（四级：RED 红危重 / ORANGE 橙急重 / YELLOW 黄急症 / GREEN 绿轻症）
 * → 就诊状态流转（WAITING→TREATING→DONE）→ 今日分级统计。
 * 与迭代 9 分诊台 {@code /api/clinic/triage}（叫号优先级）互不影响。
 */
@RestController
@RequestMapping("/api/clinic/emergency-triages")
@RequiredArgsConstructor
public class EmergencyTriageController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> LEVELS = List.of("RED", "ORANGE", "YELLOW", "GREEN");

    private final EmergencyTriageMapper triageMapper;

    /** 分诊登记 */
    @PostMapping
    @AuditLog(value = "急诊分诊登记", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.TRIAGE_MANAGE)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long patientId = toLong(body.get("patientId"));
        String patientName = str(body.get("patientName"));
        String triageLevel = str(body.get("triageLevel"));
        if (patientId == null || patientName == null || patientName.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "患者信息不能为空");
        }
        if (triageLevel == null || !LEVELS.contains(triageLevel)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "triageLevel 需为 RED/ORANGE/YELLOW/GREEN");
        }
        String visitNo = "TR" + LocalDateTime.now().format(NO_FMT) + String.format("%04d", RANDOM.nextInt(10000));
        triageMapper.insert(visitNo, patientId, patientName, triageLevel, str(body.get("chiefComplaint")),
                toDouble(body.get("temperature")), toInt(body.get("pulse")), str(body.get("bloodPressure")),
                toLong(body.get("departmentId")), toLong(body.get("doctorId")), UserContext.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", triageMapper.selectIdByVisitNo(visitNo));
        data.put("visitNo", visitNo);
        return Result.ok(data);
    }

    /** 分诊分页（分级/状态/关键字） */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.TRIAGE_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(value = "triageLevel", required = false) String triageLevel,
                                            @RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", triageMapper.selectPage(triageLevel, status, keyword, (pageNo - 1) * pageSize, pageSize));
        page.put("total", triageMapper.countPage(triageLevel, status, keyword));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 今日分级统计 */
    @GetMapping("/today-stats")
    @RequiresPermission(PermissionConstant.TRIAGE_MANAGE)
    public Result<List<Map<String, Object>>> todayStats() {
        return Result.ok(triageMapper.todayLevelStats());
    }

    /** 状态流转：WAITING→TREATING→DONE */
    @PutMapping("/{id}/status")
    @AuditLog(value = "急诊分诊状态流转", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.TRIAGE_MANAGE)
    public Result<String> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        String fromStatus = str(body.get("fromStatus"));
        if (status == null || !List.of("TREATING", "DONE").contains(status)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "status 需为 TREATING 或 DONE");
        }
        String from = "TREATING".equals(status) ? "WAITING" : (fromStatus != null ? fromStatus : "TREATING");
        if (triageMapper.updateStatus(id, from, status) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "当前状态不允许流转到 " + status);
        }
        return Result.ok("状态已更新");
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private Integer toInt(Object v) {
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }

    private Double toDouble(Object v) {
        return v == null ? null : Double.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
