package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.inpatient.entity.PatientPath;
import com.hospital.inpatient.entity.PathTemplate;
import com.hospital.inpatient.mapper.PatientPathMapper;
import com.hospital.inpatient.mapper.PathTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 患者临床路径接口（迭代12 J1）。
 * 入径（同一住院记录在径唯一）→ 推进/变异/退出/完成。
 */
@RestController
@RequestMapping("/api/inpatient/patient-path")
@RequiredArgsConstructor
public class PatientPathController {

    private final PatientPathMapper patientPathMapper;
    private final PathTemplateMapper pathTemplateMapper;

    @PostMapping("/enter")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("患者入径")
    public Result<PatientPath> enter(@RequestBody PatientPath path) {
        if (path.getTemplateId() == null || path.getAdmissionId() == null) {
            return Result.fail(1001, "路径模板与住院记录不能为空");
        }
        if (pathTemplateMapper.selectById(path.getTemplateId()) == null) {
            return Result.fail(1001, "路径模板不存在");
        }
        if (patientPathMapper.countInPath(path.getAdmissionId()) > 0) {
            return Result.fail(1001, "该住院记录已有在径路径，不可重复入径");
        }
        patientPathMapper.insert(path);
        return Result.ok(patientPathMapper.selectById(path.getId()));
    }

    @GetMapping("/{id}")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        PatientPath path = patientPathMapper.selectById(id);
        if (path == null) {
            return Result.fail(1001, "路径记录不存在");
        }
        PathTemplate template = pathTemplateMapper.selectById(path.getTemplateId());
        return Result.ok(Map.of("path", path, "template", template));
    }

    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(required = false) Long admissionId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") Integer pageNo,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<PatientPath> records = patientPathMapper.selectPage(admissionId, status, (pageNo - 1) * size, size);
        long total = patientPathMapper.countPage(admissionId, status);
        return Result.ok(Map.of("records", records, "total", total, "pageNo", pageNo, "pageSize", size));
    }

    @PostMapping("/{id}/advance")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("临床路径推进")
    public Result<String> advance(@PathVariable Long id) {
        return patientPathMapper.advance(id) > 0
                ? Result.ok("已推进一天")
                : Result.fail(1001, "仅 IN_PATH 状态路径可推进");
    }

    @PostMapping("/{id}/variation")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("临床路径变异登记")
    public Result<String> variation(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            return Result.fail(1001, "变异原因不能为空");
        }
        return patientPathMapper.vary(id, reason) > 0
                ? Result.ok("已登记变异")
                : Result.fail(1001, "仅 IN_PATH 状态路径可登记变异");
    }

    @PostMapping("/{id}/exit")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("临床路径退出")
    public Result<String> exit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            return Result.fail(1001, "退出原因不能为空");
        }
        return patientPathMapper.exit(id, reason) > 0
                ? Result.ok("已退出路径")
                : Result.fail(1001, "仅 IN_PATH/VARIATION 状态路径可退出");
    }

    @PostMapping("/{id}/complete")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("临床路径完成")
    public Result<String> complete(@PathVariable Long id) {
        return patientPathMapper.complete(id) > 0
                ? Result.ok("路径已完成")
                : Result.fail(1001, "仅 IN_PATH/VARIATION 状态路径可完成");
    }
}
