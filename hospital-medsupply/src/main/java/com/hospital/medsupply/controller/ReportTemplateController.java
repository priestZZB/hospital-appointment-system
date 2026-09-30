package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ReportTemplate;
import com.hospital.medsupply.service.ReportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报告模板接口（D4，技师/医生维护，医生开报告套模板）
 * <p>
 * 模板按 modality + body_part 组织，template_type = FINDING-所见 / CONCLUSION-印象；
 * body_part 为空表示通用模板，套用时精确部位优先于通用。
 */
@RestController
@RequestMapping("/api/admin/exam/template")
@RequiredArgsConstructor
public class ReportTemplateController {

    private final ReportTemplateService templateService;

    /** 新增模板（body: modality / bodyPart / templateType / content） */
    @AuditLog(value = "报告模板新增", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_TEMPLATE_MANAGE)
    @PostMapping
    public Result<ReportTemplate> create(@RequestBody ReportTemplate template) {
        return Result.ok(templateService.create(template));
    }

    /** 更新模板（传入字段覆盖，未传字段保留原值） */
    @AuditLog(value = "报告模板更新", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_TEMPLATE_MANAGE)
    @PutMapping("/{id}")
    public Result<ReportTemplate> update(@PathVariable("id") Long id,
                                         @RequestBody ReportTemplate template) {
        return Result.ok(templateService.update(id, template));
    }

    /** 删除模板（逻辑删除：status 置 0 停用） */
    @AuditLog(value = "报告模板删除", operationType = "DELETE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_TEMPLATE_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        templateService.delete(id);
        return Result.ok();
    }

    /** 模板分页（检查类别可选，含停用模板） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_TEMPLATE_MANAGE)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "modality", required = false) String modality,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return Result.ok(templateService.listByPage(modality, pageNo, pageSize));
    }

    /**
     * 套模板（医生开报告时用）：返回 {findings 所见模板, conclusion 印象模板}
     * <p>
     * bodyPart 精确匹配优先，无则退通用（body_part IS NULL）模板；
     * 复用影像查询权限（模板为影像报告的辅助字典数据，医技人员均可用）。
     */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_QUERY)
    @GetMapping("/apply")
    public Result<Map<String, String>> apply(
            @RequestParam("modality") String modality,
            @RequestParam(value = "bodyPart", required = false) String bodyPart) {
        return Result.ok(templateService.applyTemplate(modality, bodyPart));
    }
}
