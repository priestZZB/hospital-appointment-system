package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.QueuePriorityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法层接口（迭代15 B1-2 优先级叫号 + B1-3 停诊重调度，base=/api/clinic）。
 * 优先级叫号：多因子评分（预检级别权重 + 等待时长衰减 + 就诊状态）。
 * 停诊重调度：受影响预约识别 + 同科室替诊医生推荐。
 */
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class QueuePriorityController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Integer> LEVEL_WEIGHT = Map.of(
            "RED", 300, "ORANGE", 200, "YELLOW", 100, "GREEN", 50);

    private final QueuePriorityMapper queueMapper;

    /** 急诊优先级叫号队列（多因子评分降序） */
    @GetMapping("/queue-priority")
    @RequiresPermission(PermissionConstant.QUEUE_PRIORITY)
    public Result<List<Map<String, Object>>> queuePriority() {
        List<Map<String, Object>> queue = queueMapper.todayQueue();
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> row : queue) {
            String level = String.valueOf(row.get("triageLevel"));
            int levelScore = LEVEL_WEIGHT.getOrDefault(level, 0);
            long waitMinutes = row.get("waitMinutes") == null ? 0
                    : (long) Double.parseDouble(String.valueOf(row.get("waitMinutes")));
            // 等待时长因子：每等 5 分钟 +1，封顶 60
            int waitScore = (int) Math.min(60, waitMinutes / 5);
            // 就诊中优先（已在诊室），待就诊排前于同分
            int statusScore = "TREATING".equals(String.valueOf(row.get("status"))) ? -1000 : 0;
            int score = levelScore + waitScore + statusScore;
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("score", score);
            item.put("levelScore", levelScore);
            item.put("waitScore", waitScore);
            scored.add(item);
        }
        scored.sort(Comparator.comparingInt(m -> -((Number) m.get("score")).intValue()));
        return Result.ok(scored);
    }

    /** 停诊重调度：识别受影响预约并生成替诊建议 */
    @PostMapping("/schedule-stop/reschedule")
    @AuditLog(value = "停诊重调度生成", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.STOP_RESCHEDULE)
    public Result<Map<String, Object>> stopReschedule(@RequestBody Map<String, Object> body) {
        Long doctorId = toLong(body.get("doctorId"));
        String stopDate = String.valueOf(body.get("stopDate"));
        if (doctorId == null || stopDate == null || stopDate.isBlank() || "null".equals(stopDate)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "doctorId 与 stopDate 不能为空");
        }
        try {
            java.time.LocalDate.parse(stopDate);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "stopDate 格式应为 yyyy-MM-dd");
        }
        List<Map<String, Object>> affected = queueMapper.affectedAppointments(doctorId, stopDate);
        Long departmentId = queueMapper.doctorDepartment(doctorId);
        List<Map<String, Object>> alternatives = departmentId == null ? List.of()
                : queueMapper.alternativeDoctors(departmentId, doctorId, stopDate);
        // 建议轮转分配到替诊医生
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (int i = 0; i < affected.size(); i++) {
            Map<String, Object> sug = new LinkedHashMap<>();
            sug.put("appointmentId", affected.get(i).get("appointmentId"));
            sug.put("patientId", affected.get(i).get("patientId"));
            Map<String, Object> alt = alternatives.isEmpty() ? null : alternatives.get(i % alternatives.size());
            sug.put("suggestDoctorId", alt == null ? null : alt.get("doctorId"));
            sug.put("suggestDoctorName", alt == null ? "（无同科室可替医生，建议退号）" : alt.get("doctorName"));
            suggestions.add(sug);
        }
        String batchNo = "RS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", RANDOM.nextInt(1000));
        String suggestionsJson = toJson(suggestions);
        queueMapper.insertBatch(batchNo, doctorId, stopDate, affected.size(), suggestionsJson);
        Map<String, Object> data = new HashMap<>();
        data.put("batchNo", batchNo);
        data.put("affectedCount", affected.size());
        data.put("affected", affected);
        data.put("alternatives", alternatives);
        data.put("suggestions", suggestions);
        return Result.ok(data);
    }

    /** 重调度批次分页 */
    @GetMapping("/schedule-stop/batches")
    @RequiresPermission(PermissionConstant.STOP_RESCHEDULE)
    public Result<Map<String, Object>> batches(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", queueMapper.selectBatchPage((pageNo - 1) * pageSize, pageSize));
        page.put("total", queueMapper.countBatchPage());
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 批次标记已通知 */
    @PostMapping("/schedule-stop/batches/{id}/notify")
    @RequiresPermission(PermissionConstant.STOP_RESCHEDULE)
    public Result<String> notify(@PathVariable("id") Long id) {
        if (queueMapper.markNotified(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批次不存在或已通知");
        }
        return Result.ok("已标记通知完成");
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    /** 轻量 JSON 序列化（仅 Map/List/标量，避免引入额外依赖） */
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
