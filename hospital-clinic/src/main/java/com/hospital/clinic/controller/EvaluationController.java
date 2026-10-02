package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.EvaluationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 就诊满意度评价接口（迭代13 K3，base=/api/clinic/evaluations）。
 * 患者按就诊单提交 1~5 星评价（一次就诊一评）；医生评价聚合（公开）；管理端分页。
 */
@RestController
@RequestMapping("/api/clinic/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationMapper evaluationMapper;

    /** 患者提交评价 */
    @PostMapping
    @AuditLog(value = "提交满意度评价", operationType = "INSERT")
    public Result<String> submit(@RequestBody Map<String, Object> body) {
        Long appointmentId = toLong(body.get("appointmentId"));
        Long doctorId = toLong(body.get("doctorId"));
        Integer score = toInt(body.get("score"));
        if (appointmentId == null || doctorId == null || score == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "就诊单/医生/评分不能为空");
        }
        if (score < 1 || score > 5) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "评分需在 1~5 星之间");
        }
        if (evaluationMapper.existsByAppointment(appointmentId) > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该就诊单已评价过");
        }
        evaluationMapper.insert(appointmentId, UserContext.getUserId(), doctorId,
                toLong(body.get("departmentId")), score, str(body.get("content")));
        return Result.ok("评价已提交");
    }

    /** 某医生的评价聚合（均分+条数+最近20条；公开） */
    @GetMapping("/doctor/{doctorId}")
    public Result<Map<String, Object>> byDoctor(@PathVariable("doctorId") Long doctorId) {
        Map<String, Object> summary = evaluationMapper.doctorSummary(doctorId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("doctorId", doctorId);
        data.put("count", summary.get("count"));
        data.put("avgScore", summary.get("avgScore"));
        data.put("list", evaluationMapper.selectByDoctor(doctorId));
        return Result.ok(data);
    }

    /** 评价分页（管理端） */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.EVALUATION_VIEW)
    public Result<Map<String, Object>> list(@RequestParam(value = "doctorId", required = false) Long doctorId,
                                            @RequestParam(value = "minScore", required = false) Integer minScore,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> records = evaluationMapper.selectPage(doctorId, minScore,
                (pageNo - 1) * pageSize, pageSize);
        long total = evaluationMapper.countPage(doctorId, minScore);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", records);
        page.put("total", total);
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private Integer toInt(Object v) {
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
