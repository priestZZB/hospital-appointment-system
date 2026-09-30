package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ReportTemplate;
import com.hospital.medsupply.mapper.ReportTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告模板服务（D4 报告模板：所见/印象，按类别与部位）
 * <p>
 * 技师/医生维护所见（FINDING）与印象（CONCLUSION）模板；
 * 医生开影像报告时按 modality + bodyPart 套模板，精确部位优先，无则退通用模板。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

    private final ReportTemplateMapper templateMapper;

    private static final String TYPE_FINDING = "FINDING";
    private static final String TYPE_CONCLUSION = "CONCLUSION";

    /**
     * 新增模板
     *
     * @param template 模板（modality/content 必填，templateType 默认 FINDING）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportTemplate create(ReportTemplate template) {
        validate(template);
        if (template.getTemplateType() == null || template.getTemplateType().isBlank()) {
            template.setTemplateType(TYPE_FINDING);
        }
        template.setStatus(1);
        templateMapper.insert(template);
        log.info("[报告模板] 新增: id={}, modality={}, bodyPart={}, type={}",
                template.getId(), template.getModality(), template.getBodyPart(), template.getTemplateType());
        return templateMapper.selectById(template.getId());
    }

    /**
     * 更新模板（传入字段覆盖，未传字段保留原值）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportTemplate update(Long id, ReportTemplate patch) {
        ReportTemplate existing = requireTemplate(id);
        if (patch.getModality() != null && !patch.getModality().isBlank()) {
            existing.setModality(patch.getModality());
        }
        if (patch.getBodyPart() != null) {
            existing.setBodyPart(patch.getBodyPart().isBlank() ? null : patch.getBodyPart());
        }
        if (patch.getTemplateType() != null && !patch.getTemplateType().isBlank()) {
            existing.setTemplateType(patch.getTemplateType());
        }
        if (patch.getContent() != null && !patch.getContent().isBlank()) {
            existing.setContent(patch.getContent());
        }
        validate(existing);
        templateMapper.update(existing);
        log.info("[报告模板] 更新: id={}", id);
        return templateMapper.selectById(id);
    }

    /** 模板详情 */
    public ReportTemplate getById(Long id) {
        return requireTemplate(id);
    }

    /** 删除模板（逻辑删除：status 置 0 停用） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        templateMapper.delete(id);
        log.info("[报告模板] 停用: id={}", id);
    }

    /** 模板分页（检查类别可选） */
    public Map<String, Object> listByPage(String modality, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<ReportTemplate> records = templateMapper.selectByPage(modality, offset, pageSize);
        long total = templateMapper.countPage(modality);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 套模板（医生开报告用）：按 modality + bodyPart 返回 {findings 所见模板, conclusion 印象模板}
     * <p>
     * bodyPart 精确匹配优先，无精确模板时退通用（body_part IS NULL）模板；
     * 无任何命中时对应字段为 null。
     *
     * @param modality 检查类别（必填）
     * @param bodyPart 部位（可空 = 仅通用模板）
     */
    public Map<String, String> applyTemplate(String modality, String bodyPart) {
        if (modality == null || modality.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "modality 不能为空");
        }
        List<ReportTemplate> templates = templateMapper.selectActive(modality,
                bodyPart == null || bodyPart.isBlank() ? null : bodyPart);

        // selectActive 已保证精确部位排在通用之前，同类型取第一条即精确优先
        Map<String, String> result = new LinkedHashMap<>();
        result.put("findings", null);
        result.put("conclusion", null);
        for (ReportTemplate template : templates) {
            if (TYPE_CONCLUSION.equals(template.getTemplateType())) {
                if (result.get("conclusion") == null) {
                    result.put("conclusion", template.getContent());
                }
            } else if (TYPE_FINDING.equals(template.getTemplateType())) {
                if (result.get("findings") == null) {
                    result.put("findings", template.getContent());
                }
            }
        }
        return result;
    }

    private void validate(ReportTemplate template) {
        if (template == null || template.getModality() == null || template.getModality().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "modality 不能为空");
        }
        if (template.getContent() == null || template.getContent().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "content 不能为空");
        }
        String type = template.getTemplateType();
        if (type != null && !TYPE_FINDING.equals(type) && !TYPE_CONCLUSION.equals(type)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "templateType 仅支持 FINDING / CONCLUSION");
        }
    }

    private ReportTemplate requireTemplate(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "id 不能为空");
        }
        ReportTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "报告模板不存在");
        }
        return template;
    }
}
