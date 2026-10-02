package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.inpatient.mapper.BedPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排床优化接口（迭代15 B1-5，base=/api/inpatient/bed-plan）。
 * 待入床患者按优先级（急诊优先 → 等级高优先 → 排队早优先）与空闲床位就近匹配，
 * 生成方案留痕（不直接占床，由护士站确认后走原入院流程）。
 */
@RestController
@RequestMapping("/api/inpatient/bed-plan")
@RequiredArgsConstructor
public class BedPlanController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BedPlanMapper bedPlanMapper;

    /** 生成排床方案：body = { departmentId?, patients: [{patientId, patientName?, priority(1~5), queuedAt?}] } */
    @PostMapping("/generate")
    @AuditLog(value = "排床优化方案生成", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.BEDPLAN_MANAGE)
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long departmentId = toLong(body.get("departmentId"));
        Object patientsObj = body.get("patients");
        if (!(patientsObj instanceof List<?> patients) || patients.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "patients 不能为空");
        }
        // 1. 患者按优先级排序：priority 小者优先，同级按排队时间早者优先
        List<Map<String, Object>> sorted = new ArrayList<>();
        for (Object o : patients) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("patientId", toLong(((Map<?, ?>) m).get("patientId")));
                Object name = ((Map<?, ?>) m).get("patientName");
                row.put("patientName", name == null ? null : String.valueOf(name));
                row.put("priority", toLong(((Map<?, ?>) m).get("priority")) == null ? 5
                        : toLong(((Map<?, ?>) m).get("priority")));
                Object q = ((Map<?, ?>) m).get("queuedAt");
                row.put("queuedAt", q == null ? "" : String.valueOf(q));
                if (row.get("patientId") == null) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "patients 内 patientId 不能为空");
                }
                sorted.add(row);
            }
        }
        sorted.sort(Comparator
                .comparingLong((Map<String, Object> m) -> (Long) m.get("priority"))
                .thenComparing(m -> String.valueOf(m.get("queuedAt"))));
        // 2. 空闲床位就近匹配
        List<Map<String, Object>> beds = bedPlanMapper.availableBeds(departmentId);
        List<Map<String, Object>> suggestions = new ArrayList<>();
        int matched = 0;
        int unmatched = 0;
        for (Map<String, Object> p : sorted) {
            Map<String, Object> sug = new LinkedHashMap<>(p);
            if (matched < beds.size()) {
                Map<String, Object> bed = beds.get(matched);
                sug.put("bedId", bed.get("bedId"));
                sug.put("roomNo", bed.get("roomNo"));
                sug.put("bedNo", bed.get("bedNo"));
                sug.put("deptName", bed.get("deptName"));
                sug.put("dailyFee", bed.get("dailyFee"));
                matched++;
            } else {
                sug.put("bedId", null);
                unmatched++;
            }
            suggestions.add(sug);
        }
        // 3. 留痕
        String planNo = "BP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", RANDOM.nextInt(1000));
        String planDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        bedPlanMapper.insertPlan(planNo, planDate, toJson(suggestions), matched, unmatched);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planNo", planNo);
        data.put("planDate", planDate);
        data.put("matchedCnt", matched);
        data.put("unmatchedCnt", unmatched);
        data.put("suggestions", suggestions);
        return Result.ok(data);
    }

    /** 方案分页 */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.BEDPLAN_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", bedPlanMapper.selectPage((pageNo - 1) * pageSize, pageSize));
        page.put("total", bedPlanMapper.countPage());
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private String toJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = list.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            int j = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (j++ > 0) {
                    sb.append(",");
                }
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                sb.append(v == null ? "null" : (v instanceof Number ? v : "\"" + v + "\""));
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }
}
