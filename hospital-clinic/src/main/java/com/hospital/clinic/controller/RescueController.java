package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.RescueMapper;
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
 * 抢救记录接口（迭代14 G2，base=/api/clinic/rescues）。
 * 开始抢救（ONGOING）→ 结束抢救并落结局（SUCCESS/DEATH）。
 */
@RestController
@RequestMapping("/api/clinic/rescues")
@RequiredArgsConstructor
public class RescueController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RescueMapper rescueMapper;

    /** 开始抢救 */
    @PostMapping
    @AuditLog(value = "开始抢救记录", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.RESCUE_MANAGE)
    public Result<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        Long patientId = toLong(body.get("patientId"));
        String patientName = str(body.get("patientName"));
        if (patientId == null || patientName == null || patientName.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "患者信息不能为空");
        }
        String rescueNo = "RS" + LocalDateTime.now().format(NO_FMT) + String.format("%04d", RANDOM.nextInt(10000));
        rescueMapper.insert(rescueNo, patientId, patientName, toLong(body.get("triageId")),
                str(body.get("measures")), str(body.get("participants")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", rescueMapper.selectIdByNo(rescueNo));
        data.put("rescueNo", rescueNo);
        return Result.ok(data);
    }

    /** 抢救记录分页（outcome/keyword） */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.RESCUE_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(value = "outcome", required = false) String outcome,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", rescueMapper.selectPage(outcome, keyword, (pageNo - 1) * pageSize, pageSize));
        page.put("total", rescueMapper.countPage(outcome, keyword));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 结束抢救（outcome = SUCCESS / DEATH） */
    @PutMapping("/{id}/finish")
    @AuditLog(value = "结束抢救记录", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.RESCUE_MANAGE)
    public Result<String> finish(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String outcome = str(body.get("outcome"));
        if (outcome == null || !List.of("SUCCESS", "DEATH").contains(outcome)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "outcome 需为 SUCCESS 或 DEATH");
        }
        if (rescueMapper.finish(id, outcome) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅进行中的抢救可结束");
        }
        return Result.ok("抢救已结束");
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
