package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.inpatient.entity.ReportForm;
import com.hospital.inpatient.mapper.ReportFormMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 传染病/不良事件上报登记接口（迭代12 I2）。
 * 上报 → 审核（SUBMITTED→REVIEWED）→ 列表检索。
 */
@RestController
@RequestMapping("/api/inpatient/report-form")
@RequiredArgsConstructor
public class ReportFormController {

    private final ReportFormMapper reportFormMapper;

    @PostMapping
    @RequiresPermission(PermissionConstant.REPORT_FORM_MANAGE)
    @AuditLog("传染病/不良事件上报登记")
    public Result<ReportForm> create(@RequestBody ReportForm form) {
        if (form.getReportType() == null || form.getReportType().isBlank()
                || !"INFECTIOUS".equals(form.getReportType()) && !"ADVERSE_EVENT".equals(form.getReportType())) {
            return Result.fail(1001, "上报类型仅支持 INFECTIOUS（传染病）/ ADVERSE_EVENT（不良事件）");
        }
        if (form.getEventName() == null || form.getEventName().isBlank()) {
            return Result.fail(1001, "事件名称不能为空");
        }
        if (form.getEventTime() == null) {
            form.setEventTime(java.time.LocalDateTime.now());
        }
        reportFormMapper.insert(form);
        return Result.ok(reportFormMapper.selectById(form.getId()));
    }

    @GetMapping("/{id}")
    @RequiresPermission(PermissionConstant.REPORT_FORM_MANAGE)
    public Result<ReportForm> detail(@PathVariable Long id) {
        ReportForm form = reportFormMapper.selectById(id);
        return form == null ? Result.fail(1001, "上报记录不存在") : Result.ok(form);
    }

    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.REPORT_FORM_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(required = false) String reportType,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") Integer pageNo,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<ReportForm> records = reportFormMapper.selectPage(reportType, status, (pageNo - 1) * size, size);
        long total = reportFormMapper.countPage(reportType, status);
        return Result.ok(Map.of("records", records, "total", total, "pageNo", pageNo, "pageSize", size));
    }

    @PutMapping("/{id}/review")
    @RequiresPermission(PermissionConstant.REPORT_FORM_REVIEW)
    @AuditLog("上报登记审核")
    public Result<String> review(@PathVariable Long id,
                                 @RequestParam(value = "note", required = false) String note) {
        return reportFormMapper.review(id, note) > 0
                ? Result.ok("审核完成")
                : Result.fail(1001, "仅待审核（SUBMITTED）记录可审核");
    }
}
