package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ExamItemMapper;
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
    private final ExamApplicationMapper examApplicationMapper;
    private final FileService fileService;

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
     * 管理员录入检查报告（可选附件上传 MinIO）
     *
     * @param applicationId 检查申请 ID
     * @param reportDesc    报告描述
     * @param reportResult  检查结果/诊断
     * @param file          附件文件（可选）
     * @param status        DRAFT/PUBLISHED（默认 PUBLISHED）
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
        String reportStatus = (status == null || status.isBlank()) ? "PUBLISHED" : status;
        if (!"DRAFT".equals(reportStatus) && !"PUBLISHED".equals(reportStatus)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报告状态仅支持 DRAFT / PUBLISHED");
        }
        report.setStatus(reportStatus);
        if ("PUBLISHED".equals(reportStatus)) {
            report.setCompleteTime(LocalDateTime.now());
        }
        examReportMapper.insert(report);
        examApplicationMapper.updateStatus(applicationId, "COMPLETED");
        log.info("[检查报告] 录入成功: applicationId={}, reportId={}, status={}",
                applicationId, report.getId(), reportStatus);
        return report;
    }
}
