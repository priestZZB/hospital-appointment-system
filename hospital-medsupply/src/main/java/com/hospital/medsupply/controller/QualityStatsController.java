package com.hospital.medsupply.controller;

import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.medsupply.mapper.QualityStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 医疗质量指标看板接口（迭代12补全 J2）。
 * GET /api/medsupply/quality/indicators —— 危急值闭环率 / 报告完成率（及时率）/ 标本采集情况。
 * date 为空 = 全量累计口径；传 YYYY-MM-DD = 当日口径。
 */
@RestController
@RequestMapping("/api/medsupply/quality")
@RequiredArgsConstructor
public class QualityStatsController {

    private final QualityStatsMapper qualityStatsMapper;

    @GetMapping("/indicators")
    @RequiresPermission(PermissionConstant.QUALITY_VIEW)
    public Result<Map<String, Object>> indicators(@RequestParam(value = "date", required = false) String date) {
        Map<String, Object> critical = qualityStatsMapper.criticalSummary(date);
        Map<String, Object> report = qualityStatsMapper.reportSummary(date);
        Map<String, Object> specimen = qualityStatsMapper.specimenSummary(date);

        long criticalTotal = toLong(critical.get("total"));
        long criticalClosed = toLong(critical.get("closed"));
        long reportTotal = toLong(report.get("total"));
        long reportDone = toLong(report.get("done"));

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> criticalBlock = new LinkedHashMap<>();
        criticalBlock.put("total", criticalTotal);
        criticalBlock.put("closed", criticalClosed);
        criticalBlock.put("closeRate", criticalTotal == 0 ? null
                : Math.round(criticalClosed * 1000.0 / criticalTotal) / 10.0);
        data.put("critical", criticalBlock);

        Map<String, Object> reportBlock = new LinkedHashMap<>();
        reportBlock.put("total", reportTotal);
        reportBlock.put("done", reportDone);
        reportBlock.put("doneRate", reportTotal == 0 ? null
                : Math.round(reportDone * 1000.0 / reportTotal) / 10.0);
        reportBlock.put("avgHours", report.get("avgHours"));
        data.put("report", reportBlock);

        Map<String, Object> specimenBlock = new LinkedHashMap<>();
        specimenBlock.put("total", toLong(specimen.get("total")));
        specimenBlock.put("collected", toLong(specimen.get("collected")));
        data.put("specimen", specimenBlock);

        return Result.ok(data);
    }

    private long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
