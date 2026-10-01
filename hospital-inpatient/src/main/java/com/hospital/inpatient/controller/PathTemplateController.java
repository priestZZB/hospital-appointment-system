package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.inpatient.entity.PathTemplate;
import com.hospital.inpatient.mapper.PathTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 临床路径模板接口（迭代12 J1） */
@RestController
@RequestMapping("/api/inpatient/path-template")
@RequiredArgsConstructor
public class PathTemplateController {

    private final PathTemplateMapper pathTemplateMapper;

    @PostMapping
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("临床路径模板创建")
    public Result<PathTemplate> create(@RequestBody PathTemplate template) {
        if (template.getPathCode() == null || template.getPathCode().isBlank()) {
            return Result.fail(1001, "路径编码不能为空");
        }
        if (template.getPathName() == null || template.getPathName().isBlank()) {
            return Result.fail(1001, "路径名称不能为空");
        }
        if (template.getStandardDays() == null || template.getStandardDays() < 1) {
            template.setStandardDays(1);
        }
        if (template.getItemJson() != null && !template.getItemJson().isBlank()) {
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(template.getItemJson());
            } catch (Exception e) {
                return Result.fail(1001, "阶段医嘱项 itemJson 不是合法 JSON：" + e.getMessage());
            }
        }
        pathTemplateMapper.insert(template);
        return Result.ok(pathTemplateMapper.selectById(template.getId()));
    }

    @PutMapping("/{id}")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    @AuditLog("临床路径模板编辑")
    public Result<String> update(@PathVariable Long id, @RequestBody PathTemplate template) {
        if (pathTemplateMapper.selectById(id) == null) {
            return Result.fail(1001, "路径模板不存在");
        }
        template.setId(id);
        pathTemplateMapper.update(template);
        return Result.ok("模板已更新");
    }

    @GetMapping("/{id}")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    public Result<PathTemplate> detail(@PathVariable Long id) {
        PathTemplate template = pathTemplateMapper.selectById(id);
        return template == null ? Result.fail(1001, "路径模板不存在") : Result.ok(template);
    }

    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.PATIENT_PATH_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") Integer pageNo,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<PathTemplate> records = pathTemplateMapper.selectPage(keyword, (pageNo - 1) * size, size);
        long total = pathTemplateMapper.countPage(keyword);
        return Result.ok(Map.of("records", records, "total", total, "pageNo", pageNo, "pageSize", size));
    }
}
