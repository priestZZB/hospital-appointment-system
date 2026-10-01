package com.hospital.clinic.controller;

import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.ClinicStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 门诊统计报表接口（迭代12 I1）。
 * 日报（挂号/接诊/处方/收入/科室TOP5）+ 月报（含逐日序列）+ CSV 导出（UTF-8 BOM，Excel 直开）。
 */
@RestController
@RequestMapping("/api/clinic/stats")
@RequiredArgsConstructor
public class ClinicStatsController {

    private final ClinicStatsMapper statsMapper;

    @GetMapping("/daily")
    @RequiresPermission(PermissionConstant.CLINIC_STATS_DAILY)
    public Result<Map<String, Object>> daily(@RequestParam String date) {
        return Result.ok(dailyMap(date));
    }

    @GetMapping("/monthly")
    @RequiresPermission(PermissionConstant.CLINIC_STATS_MONTHLY)
    public Result<Map<String, Object>> monthly(@RequestParam String month) {
        long register = 0;
        BigDecimal income = BigDecimal.ZERO;
        List<Map<String, Object>> series = mergeSeries(month);
        for (Map<String, Object> row : series) {
            Object c = row.get("count");
            if (c instanceof Number n) {
                register += n.longValue();
            }
            Object inc = row.get("income");
            if (inc instanceof BigDecimal bd) {
                income = income.add(bd);
            }
        }
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("month", month);
        vo.put("registerCount", register);
        vo.put("totalIncome", income);
        vo.put("dailySeries", series);
        return Result.ok(vo);
    }

    @GetMapping("/daily/export")
    @RequiresPermission(PermissionConstant.CLINIC_STATS_EXPORT)
    public ResponseEntity<byte[]> dailyExport(@RequestParam String date) {
        Map<String, Object> d = dailyMap(date);
        StringBuilder sb = csvHeader("门诊日报", date);
        sb.append("挂号数,").append(d.get("registerCount")).append("\r\n");
        sb.append("接诊数（实际报到）,").append(d.get("consultCount")).append("\r\n");
        sb.append("处方数,").append(d.get("prescriptionCount")).append("\r\n");
        sb.append("挂号费收入,").append(d.get("registerFee")).append("\r\n");
        sb.append("处方费收入,").append(d.get("prescriptionAmount")).append("\r\n");
        sb.append("门诊总收入,").append(d.get("totalIncome")).append("\r\n");
        Object top = d.get("topDepartments");
        if (top instanceof List<?> rows) {
            sb.append("\r\n科室TOP5\r\n科室,挂号数\r\n");
            for (Object o : rows) {
                if (o instanceof Map<?, ?> m) {
                    sb.append(m.get("deptName")).append(",").append(m.get("count")).append("\r\n");
                }
            }
        }
        return csvResponse("clinic-daily-" + date + ".csv", sb.toString());
    }

    @GetMapping("/monthly/export")
    @RequiresPermission(PermissionConstant.CLINIC_STATS_EXPORT)
    public ResponseEntity<byte[]> monthlyExport(@RequestParam String month) {
        StringBuilder sb = csvHeader("门诊月报", month);
        sb.append("日期,挂号数,收入\r\n");
        for (Map<String, Object> row : mergeSeries(month)) {
            sb.append(row.get("day")).append(",").append(row.get("count")).append(",")
                    .append(row.get("income")).append("\r\n");
        }
        return csvResponse("clinic-monthly-" + month + ".csv", sb.toString());
    }

    // ---------- 内部 ----------

    private Map<String, Object> dailyMap(String date) {
        BigDecimal registerFee = statsMapper.sumRegisterFee(date);
        BigDecimal prescriptionAmount = statsMapper.sumPrescriptionAmount(date);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("date", date);
        vo.put("registerCount", statsMapper.countRegister(date));
        vo.put("consultCount", statsMapper.countConsult(date));
        vo.put("prescriptionCount", statsMapper.countPrescription(date));
        vo.put("registerFee", registerFee);
        vo.put("prescriptionAmount", prescriptionAmount);
        vo.put("totalIncome", registerFee.add(prescriptionAmount));
        vo.put("topDepartments", statsMapper.topDepartments(date));
        return vo;
    }

    private List<Map<String, Object>> mergeSeries(String month) {
        Map<String, Map<String, Object>> byDay = new TreeMap<>();
        for (Map<String, Object> row : statsMapper.registerSeries(month)) {
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
