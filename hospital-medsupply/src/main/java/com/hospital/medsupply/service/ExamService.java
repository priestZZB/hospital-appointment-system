package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.realtime.RealtimePublisher;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ExamItemMapper;
import com.hospital.medsupply.entity.CriticalValue;
import com.hospital.medsupply.mapper.CriticalValueMapper;
import com.hospital.medsupply.mapper.ExamReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamItemMapper examItemMapper;
    private final ExamReportMapper examReportMapper;
    private final CriticalValueMapper criticalValueMapper;
    private final ExamApplicationMapper examApplicationMapper;
    private final FileService fileService;
    private final RealtimePublisher realtimePublisher;

    // ---- 检查项目 ----

    public List<ExamItem> itemList(String keyword, String itemType) {
        return examItemMapper.selectPage(keyword, itemType, 0, 100);
    }

    @Transactional(rollbackFor = Exception.class)
    public ExamItem createItem(ExamItem item) {
        if (examItemMapper.selectByCode(item.getItemCode()) != null)
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "项目编码已存在");
        item.setStatus(1);
        examItemMapper.insert(item);
        return item;
    }

    // ---- 检查报告 ----

    public Map<String, Object> reportPage(Long patientId, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<ExamReport> list = examReportMapper.selectByPatientId(patientId, offset, pageSize);
        long total = examReportMapper.countByPatientId(patientId);
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 技师录入检查报告（可选附件上传 MinIO）
     * <p>
     * 报告默认状态为 PENDING_AUDIT（待审核），可显式指定 DRAFT/PENDING_AUDIT/PUBLISHED。
     *
     * @param applicationId 检查申请 ID
     * @param reportDesc    报告描述
     * @param reportResult  检查结果/诊断
     * @param file          附件文件（可选）
     * @param status        DRAFT/PENDING_AUDIT/PUBLISHED（默认 PENDING_AUDIT）
     * @param operatorId    录入人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReport createReport(Long applicationId, String reportDesc, String reportResult,
                                   MultipartFile file, String status, Long operatorId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "applicationId 不能为空");
        }
        ExamApplication application = examApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "检查申请不存在");
        }
        if (examReportMapper.selectByApplicationId(applicationId) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该申请已录入检查报告");
        }

        ExamReport report = new ExamReport();
        report.setApplicationId(applicationId);
        report.setPatientId(application.getPatientId());
        report.setReportDesc(reportDesc);
        report.setReportResult(reportResult);
        report.setOperatorId(operatorId);
        if (file != null && !file.isEmpty()) {
            report.setAttachmentUrl(fileService.upload(file));
            report.setAttachmentName(file.getOriginalFilename());
        }
        String reportStatus = (status == null || status.isBlank()) ? "PENDING_AUDIT" : status;
        if (!"DRAFT".equals(reportStatus) && !"PENDING_AUDIT".equals(reportStatus)
                && !"PUBLISHED".equals(reportStatus)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报告状态仅支持 DRAFT / PENDING_AUDIT / PUBLISHED");
        }
        report.setStatus(reportStatus);
        if ("PUBLISHED".equals(reportStatus)) {
            report.setCompleteTime(LocalDateTime.now());
        }
        examReportMapper.insert(report);
        examApplicationMapper.updateStatus(applicationId, "COMPLETED");
        // 报告已发布 → 跨服务实时推送（clinic WebSocket 桥接 → 患者端订阅 /topic/report/{patientId}）
        if ("PUBLISHED".equals(reportStatus)) {
            realtimePublisher.publishToPatient(application.getPatientId(), "REPORT_PUBLISHED",
                    Map.of("reportId", report.getId(), "applicationId", applicationId,
                            "patientId", application.getPatientId(), "examItemName", application.getExamItemName()));
        }
        log.info("[检查报告] 录入成功: applicationId={}, reportId={}, status={}",
                applicationId, report.getId(), reportStatus);
        return report;
    }

    /**
     * 报告审核（发布/驳回）
     *
     * @param reportId     报告 ID
     * @param status       目标状态（PUBLISHED / REJECTED）
     * @param auditComment 审核意见
     * @param auditorId    审核人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReport auditReport(Long reportId, String status, String auditComment, Long auditorId) {
        if (reportId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "reportId 不能为空");
        }
        if (!"PUBLISHED".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核状态仅支持 PUBLISHED / REJECTED");
        }
        ExamReport report = examReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "检查报告不存在");
        }
        int rows = examReportMapper.audit(reportId, status, auditorId, auditComment);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报告审核失败");
        }
        if ("PUBLISHED".equals(status)) {
            // 审核通过发布 → 跨服务实时推送 + 结果命中高危词自动登记危急值
            realtimePublisher.publishToPatient(report.getPatientId(), "REPORT_PUBLISHED",
                    Map.of("reportId", reportId, "applicationId", report.getApplicationId(),
                            "patientId", report.getPatientId()));
            autoRegisterCritical(report, auditorId);
        } else if ("REJECTED".equals(status)) {
            // 报告驳回 → 检查申请退回待执行，允许技师重录
            examApplicationMapper.updateStatus(report.getApplicationId(), "PENDING");
        }
        log.info("[检查报告] 审核完成: reportId={}, status={}, auditorId={}", reportId, status, auditorId);
        return examReportMapper.selectById(reportId);
    }

    // ---- 检查执行 ----

    /**
     * 检查执行登记：状态 PENDING → EXECUTING
     *
     * @param applicationId 检查申请 ID
     * @param operatorId    执行技师 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamApplication executeExam(Long applicationId, Long operatorId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "applicationId 不能为空");
        }
        ExamApplication application = examApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.EXAM_APPLICATION_NOT_FOUND);
        }
        if (!"PAID".equals(application.getPayStatus())) {
            throw new BusinessException(ErrorCodeEnum.PAY_NOT_COMPLETED, "检查尚未缴费，不可执行");
        }
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅待执行状态的检查申请可执行登记");
        }
        int rows = examApplicationMapper.markExecuted(applicationId, "EXECUTING", operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "执行登记失败，状态已变更");
        }
        log.info("[检查执行] 执行登记成功: applicationId={}, operatorId={}", applicationId, operatorId);
        return examApplicationMapper.selectById(applicationId);
    }

    /**
     * 影像科（item_type != LAB）按状态查询申请列表
     *
     * @param status 申请状态（PENDING/EXECUTING/COMPLETED/CANCELLED）
     * @param offset 偏移量
     * @param limit  每页条数
     */
    public List<ExamApplication> listImagingByStatus(String status, int offset, int limit) {
        return examApplicationMapper.selectImagingByStatus(status, offset, limit);
    }

    /**
     * 检验科（item_type = LAB）按状态查询申请列表
     *
     * @param status 申请状态（PENDING/EXECUTING/COMPLETED/CANCELLED）
     * @param offset 偏移量
     * @param limit  每页条数
     */
    public List<ExamApplication> listLabByStatus(String status, int offset, int limit) {
        return examApplicationMapper.selectLabByStatus(status, offset, limit);
    }

    /**
     * 报告发布时自动识别危急值：结果文本命中高危关键结果词（如 危急/偏高/偏低/异常 且含数值）
     * 时登记一条 PENDING 危急值记录，供医生复核。
     * 真实场景建议由检验仪器阈值规则驱动，这里做演示级关键词识别。
     */
    private void autoRegisterCritical(ExamReport report, Long auditorId) {
        try {
            String result = report.getReportResult() == null ? "" : report.getReportResult();
            String desc = report.getReportDesc() == null ? "" : report.getReportDesc();
            String text = (result + " " + desc).toUpperCase();
            boolean criticalHit = text.contains("CRITICAL") || text.contains("危急")
                    || (text.contains("偏高") && text.matches(".*\\d.*"))
                    || (text.contains("偏低") && text.matches(".*\\d.*"))
                    || text.contains("PANIC");
            if (!criticalHit) {
                return;
            }
            CriticalValue value = new CriticalValue();
            value.setReportId(report.getId());
            value.setApplicationId(report.getApplicationId());
            value.setPatientId(report.getPatientId());
            value.setItemName("报告危急值");
            value.setResultValue(result.length() > 200 ? result.substring(0, 200) : result);
            value.setReferenceRange(null);
            value.setCriticalLevel("HIGH");
            value.setReporterId(auditorId);
            criticalValueMapper.insert(value);
            log.info("[检查报告] 自动登记危急值: valueId={}, reportId={}", value.getId(), report.getId());
        } catch (Exception e) {
            // 自动识别失败不阻断审核发布
            log.warn("[检查报告] 自动危急值识别失败（忽略）: reportId={}", report.getId(), e);
        }
    }

}
