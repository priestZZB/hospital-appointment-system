package com.hospital.inpatient.controller;

import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.inpatient.mapper.InpatientStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 住院统计报表接口（迭代12 I1）。
 * 日报（入院/出院/在院/手术/押金/收入）+ 月报（含逐日序列）+ CSV 导出（UTF-8 BOM，Excel 直开）。
 */
@RestController
@RequestMapping("/api/inpatient/stats")
@RequiredArgsConstructor
public class InpatientStatsController {

    private final InpatientStatsMapper statsMapper;

    @GetMapping("/daily")
    @RequiresPermission(PermissionConstant.INPATIENT_STATS_DAILY)
    public Result<Map<String, Object>> daily(@RequestParam String date) {
        return Result.ok(dailyMap(date));
    }

    @GetMapping("/monthly")
    @RequiresPermission(PermissionConstant.INPATIENT_STATS_MONTHLY)
    public Result<Map<String, Object>> monthly(@RequestParam String month) {
        Map<String, Object> vo = new LinkedHashMap<>();
        long admit = 0;
        BigDecimal income = BigDecimal.ZERO;
        List<Map<String, Object>> series = mergeSeries(month);
        for (Map<String, Object> row : series) {
            Object c = row.get("count");
            if (c instanceof Number n) {
                admit += n.longValue();
            }
            Object inc = row.get("income");
            if (inc instanceof BigDecimal bd) {
                income = income.add(bd);
            }
        }
        vo.put("month", month);
        vo.put("admitCount", admit);
        vo.put("incomeTotal", income);
        vo.put("inWardCount", statsMapper.countInWard());
        vo.put("dailySeries", series);
        return Result.ok(vo);
    }

    @GetMapping("/daily/export")
    @RequiresPermission(PermissionConstant.INPATIENT_STATS_EXPORT)
    public ResponseEntity<byte[]> dailyExport(@RequestParam String date) {
        Map<String, Object> d = dailyMap(date);
        StringBuilder sb = csvHeader("住院日报", date);
        sb.append("入院人数,").append(d.get("admitCount")).append("\r\n");
        sb.append("出院人数,").append(d.get("dischargeCount")).append("\r\n");
        sb.append("当前在院,").append(d.get("inWardCount")).append("\r\n");
        sb.append("当日手术,").append(d.get("surgeryCount")).append("\r\n");
        sb.append("当日押金收入,").append(d.get("depositTotal")).append("\r\n");
        sb.append("当日费用收入,").append(d.get("incomeTotal")).append("\r\n");
        return csvResponse("inpatient-daily-" + date + ".csv", sb.toString());
    }

    @GetMapping("/monthly/export")
    @RequiresPermission(PermissionConstant.INPATIENT_STATS_EXPORT)
    public ResponseEntity<byte[]> monthlyExport(@RequestParam String month) {
        StringBuilder sb = csvHeader("住院月报", month);
        sb.append("日期,入院人数,费用收入\r\n");
        for (Map<String, Object> row : mergeSeries(month)) {
            sb.append(row.get("day")).append(",").append(row.get("count")).append(",")
                    .append(row.get("income")).append("\r\n");
        }
        return csvResponse("inpatient-monthly-" + month + ".csv", sb.toString());
    }

    // ---------- 内部 ----------

    private Map<String, Object> dailyMap(String date) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("date", date);
        vo.put("admitCount", statsMapper.countAdmit(date));
        vo.put("dischargeCount", statsMapper.countDischarge(date));
        vo.put("inWardCount", statsMapper.countInWard());
        vo.put("surgeryCount", statsMapper.countSurgery(date));
        vo.put("depositTotal", statsMapper.sumDeposit(date));
        vo.put("incomeTotal", statsMapper.sumIncome(date));
        return vo;
    }

    private List<Map<String, Object>> mergeSeries(String month) {
        Map<String, Map<String, Object>> byDay = new TreeMap<>();
        for (Map<String, Object> row : statsMapper.admitSeries(month)) {
            String day = String.valueOf(row.get("day"));
            byDay.computeIfAbsent(day, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("day", k);
                m.put("count", 0L);
                m.put("income", BigDecimal.ZERO);
                return m;
            }).put("count", row.get("cnt"));
        }
        for (Map<String, Object> row : statsMapper.incomeSeries(month)) {
            String day = String.valueOf(row.get("day"));
            byDay.computeIfAbsent(day, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("day", k);
                m.put("count", 0L);
                m.put("income", BigDecimal.ZERO);
                return m;
            }).put("income", row.get("income"));
        }
        return new ArrayList<>(byDay.values());
    }

    private StringBuilder csvHeader(String title, String scope) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(title).append("（").append(scope).append("）\r\n");
        sb.append("指标,数值\r\n");
        return sb;
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
