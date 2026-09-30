package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.entity.ResultItem;
import com.hospital.medsupply.mapper.ExamReportMapper;
import com.hospital.medsupply.mapper.ResultItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检验结果录入服务（迭代8 C3）
 * <p>
 * 覆盖式录入：先删除报告下全部明细再批插；同步把行摘要回写
 * exam_report.report_result（保持老接口/老页面兼容）。异常标志（↑/↓）由本服务判定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultItemService {

    /** 从 ref_range 提取数字段的正则：3.5-9.5 / 3.5-9.5×10⁹/L 均取前两段数字为下限-上限 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** 摘要拼接的行数上限（超出部分折叠为「等 N 项」） */
    private static final int SUMMARY_MAX_ROWS = 3;

    private final ResultItemMapper resultItemMapper;
    private final ExamReportMapper examReportMapper;

    /**
     * 覆盖式录入检验结果明细
     * <p>
     * 校验规则：报告存在；报告状态未审核——已审核发布（PUBLISHED）的报告拒绝重录，
     * DRAFT / PENDING_AUDIT / REJECTED（驳回后重录）允许录入。
     * （exam_report 状态词表为 DRAFT/PENDING_AUDIT/PUBLISHED/REJECTED，无 AUDITED 值，
     * 「已审核」即 PUBLISHED。）
     *
     * @param reportId   报告 ID（exam_report.id）
     * @param items      结果明细行
     * @param operatorId 录入人 ID（回写 exam_report.operator_id）
     * @return 录入后的明细列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ResultItem> saveReportResult(Long reportId, List<ResultItem> items, Long operatorId) {
        if (reportId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "reportId 不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "items 检验结果明细不能为空");
        }
        ExamReport report = examReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "检验报告不存在");
        }
        if ("PUBLISHED".equals(report.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报告已审核发布，禁止重录检验结果");
        }
        for (ResultItem item : items) {
            if (item == null || item.getItemName() == null || item.getItemName().isBlank()
                    || item.getResultValue() == null || item.getResultValue().isBlank()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "每行明细的 itemName 与 resultValue 不能为空");
            }
            item.setReportId(reportId);
            item.setAbnormalFlag(judgeAbnormalFlag(item.getResultValue(), item.getRefRange()));
            if (item.getSortOrder() == null) {
                item.setSortOrder(0);
            }
        }
        items.sort(Comparator.comparing(ResultItem::getSortOrder,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        // 覆盖式：先清空旧明细再批插，最后回写行摘要
        resultItemMapper.deleteByReportId(reportId);
        resultItemMapper.insertBatch(items);
        resultItemMapper.updateReportResult(reportId, buildSummary(items), operatorId);
        log.info("[检验结果] 覆盖式录入完成: reportId={}, rows={}, operatorId={}",
                reportId, items.size(), operatorId);
        return resultItemMapper.selectByReportId(reportId);
    }

    /**
     * 按报告查询结果明细（按排序号升序）
     */
    public List<ResultItem> listByReport(Long reportId) {
        return resultItemMapper.selectByReportId(reportId);
    }

    /**
     * 异常标志判定：result_value 可解析为数值且 ref_range 含「下限-上限」两段数字时，
     * 高于上限记 ↑，低于下限记 ↓，其余（正常/解析失败）留空。
     */
    private String judgeAbnormalFlag(String resultValue, String refRange) {
        Double value = parseNumber(resultValue);
        if (value == null || refRange == null || refRange.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(refRange);
        Double lower = matcher.find() ? Double.valueOf(matcher.group()) : null;
        Double upper = matcher.find() ? Double.valueOf(matcher.group()) : null;
        if (lower == null || upper == null) {
            return null;
        }
        if (value > upper) {
            return "↑";
        }
        if (value < lower) {
            return "↓";
        }
        return null;
    }

    /** 结果值解析：整串必须是一个数值（如 12.3 / 90 / -0.5），失败返回 null */
    private Double parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 行摘要：按排序取前 {@value SUMMARY_MAX_ROWS} 行拼为
     * 「WBC ↑12.3×10⁹/L；Hb 90g/L；...等 N 项」，供 exam_report.report_result 老接口展示。
     */
    private String buildSummary(List<ResultItem> items) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(SUMMARY_MAX_ROWS, items.size());
        for (int i = 0; i < shown; i++) {
            ResultItem item = items.get(i);
            if (i > 0) {
                sb.append("；");
            }
            sb.append(item.getItemName())
                    .append(" ")
                    .append(item.getAbnormalFlag() == null ? "" : item.getAbnormalFlag())
                    .append(item.getResultValue())
                    .append(item.getUnit() == null ? "" : item.getUnit());
        }
        if (items.size() > SUMMARY_MAX_ROWS) {
            sb.append("；...等 ").append(items.size()).append(" 项");
        }
        return sb.toString();
    }
}
