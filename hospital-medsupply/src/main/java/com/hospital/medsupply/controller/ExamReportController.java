package com.hospital.medsupply.controller;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 患者检查报告查询（本人）。
 * <p>
 * 安全说明：报告属于敏感医疗数据，患者仅可查询本人报告；
 * 传 patientId 代查他人仅限管理员（审计日志记录）。
 */
@Slf4j
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamReportController {

    private final ExamService examService;
    private final PatientFeignClient patientFeignClient;

    @GetMapping("/report/my")
    public Result<Map<String, Object>> myReports(@RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) Long patientId) {
        Long pid;
        if (patientId != null) {
            // 代查他人报告：仅管理员（患者端不传 patientId，走本人解析）
            if (!UserContext.isAdminOrSuperAdmin()) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可查询他人检查报告");
            }
            pid = patientId;
        } else {
            // 患者本人：auth userId → patient db id（auth userId ≠ patient 主键）
            pid = resolvePatientId();
        }
        if (pid == null) {
            // 未实名/患者档案缺失：返回空分页（刚注册账号尚未建档案）
            Map<String, Object> empty = new HashMap<>();
            empty.put("records", Collections.emptyList());
            empty.put("total", 0L);
            return Result.ok(empty);
        }
        return Result.ok(examService.reportPage(pid, pageNo, pageSize));
    }

    /** 通过 userId 解析 patientId（auth userId ≠ patient db id） */
    private Long resolvePatientId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.get("id") == null) {
                return null;
            }
            Object id = patientInfo.get("id");
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
            return Long.parseLong(id.toString());
        } catch (Exception e) {
            log.warn("[检查报告] 查询患者信息失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }
}
