package com.hospital.ai.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.ai.mapper.NoShowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 爽约预测接口（迭代15 B1-4，base=/api/ai/no-show）。
 * 加权特征评分：0.4×历史爽约率 + 0.3×提前天数因子 + 0.2×时段因子 + 0.1×就诊频次因子。
 * 特征由调用方（挂号/候诊场景）传入，预测结果留痕供模型自评估。
 */
@RestController
@RequestMapping("/api/ai/no-show")
@RequiredArgsConstructor
public class NoShowController {

    private final NoShowMapper noShowMapper;

    /** 预测某患者某次预约的爽约风险 */
    @PostMapping("/predict")
    @AuditLog(value = "爽约风险预测", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.NOSHOW_VIEW)
    public Result<Map<String, Object>> predict(@RequestBody Map<String, Object> body) {
        Long patientId = toLong(body.get("patientId"));
        Long appointmentId = toLong(body.get("appointmentId"));
        if (patientId == null || appointmentId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "patientId 与 appointmentId 不能为空");
        }
        int historyTotal = toInt(body.get("historyTotal")) == null ? 0 : toInt(body.get("historyTotal"));
        int historyNoShow = toInt(body.get("historyNoShow")) == null ? 0 : toInt(body.get("historyNoShow"));
        int advanceDays = toInt(body.get("advanceDays")) == null ? 1 : toInt(body.get("advanceDays"));
        int hourOfDay = toInt(body.get("hourOfDay")) == null ? 9 : toInt(body.get("hourOfDay"));

        // 0.4 历史爽约率（无历史记录给中性 0.15）
        double noShowRate = historyTotal <= 0 ? 0.15
                : Math.min(1.0, (double) historyNoShow / historyTotal);
        // 0.3 提前天数因子：提前 1~2 天最可靠（0），提前越久风险越高（封顶 1）
        double advanceFactor = advanceDays <= 2 ? 0.0 : Math.min(1.0, (advanceDays - 2) / 12.0);
        // 0.2 时段因子：清晨 7-8 点与午末 16 点后风险略高
        double hourFactor = (hourOfDay <= 8 || hourOfDay >= 16) ? 0.6 : 0.2;
        // 0.1 频次因子：就诊次数越少风险越高
        double freqFactor = historyTotal <= 0 ? 0.5 : Math.max(0.0, 1.0 - Math.min(1.0, historyTotal / 10.0));

        double score = 0.4 * noShowRate + 0.3 * advanceFactor + 0.2 * hourFactor + 0.1 * freqFactor;
        score = Math.max(0.0, Math.min(0.99, score));
        String riskLevel = score >= 0.45 ? "HIGH" : (score >= 0.25 ? "MEDIUM" : "LOW");
        String factors = String.format(
                "noShowRate=%.2f,advanceFactor=%.2f,hourFactor=%.2f,freqFactor=%.2f",
                noShowRate, advanceFactor, hourFactor, freqFactor);
        noShowMapper.insertPrediction(patientId, appointmentId, score, riskLevel, factors);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientId", patientId);
        data.put("appointmentId", appointmentId);
        data.put("score", Math.round(score * 10000) / 10000.0);
        data.put("riskLevel", riskLevel);
        data.put("factors", factors);
        data.put("suggestion", "HIGH".equals(riskLevel) ? "建议就诊前短信/电话提醒"
                : ("MEDIUM".equals(riskLevel) ? "建议预约提醒推送" : "常规提醒即可"));
        return Result.ok(data);
    }

    /** 某患者预测历史 */
    @GetMapping("/patient/{patientId}")
    @RequiresPermission(PermissionConstant.NOSHOW_VIEW)
    public Result<Map<String, Object>> byPatient(@org.springframework.web.bind.annotation.PathVariable("patientId") Long patientId) {
        Map<String, Object> data = new LinkedHashMap<>(noShowMapper.patientSummary(patientId));
        data.put("patientId", patientId);
        data.put("list", noShowMapper.selectByPatient(patientId));
        return Result.ok(data);
    }

    /** 预测记录分页 */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.NOSHOW_VIEW)
    public Result<Map<String, Object>> list(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", noShowMapper.selectPage((pageNo - 1) * pageSize, pageSize));
        page.put("total", noShowMapper.countPage());
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
}
