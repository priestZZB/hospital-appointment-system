package com.hospital.medsupply.controller;

import com.hospital.common.result.Result;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamReportController {

    private final ExamService examService;

    @GetMapping("/report/my")
    public Result<Map<String, Object>> myReports(@RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) Long patientId) {
        Long pid = patientId != null ? patientId : 1L; // 简化：实际应从 UserContext 获取
        return Result.ok(examService.reportPage(pid, pageNo, pageSize));
    }
}
