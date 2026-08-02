package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.mapper.ExamItemMapper;
import com.hospital.medsupply.mapper.ExamReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamItemMapper examItemMapper;
    private final ExamReportMapper examReportMapper;

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
}
