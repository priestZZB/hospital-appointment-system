package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.CriticalValue;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.mapper.CriticalValueMapper;
import com.hospital.medsupply.mapper.ExamReportMapper;
import com.hospital.medsupply.vo.CriticalValueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 危急值服务：技师上报 → 医生复核 → 处置闭环
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CriticalValueService {

    private final CriticalValueMapper criticalValueMapper;
    private final ExamReportMapper examReportMapper;

    @Transactional(rollbackFor = Exception.class)
    public CriticalValueVO report(Long userId, CriticalValue value) {
        if (value.getReportId() == null || value.getResultValue() == null || value.getItemName() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "报告、项目名称与结果值不能为空");
        }
        ExamReport report = examReportMapper.selectById(value.getReportId());
        if (report == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "检查报告不存在");
        }
        value.setApplicationId(report.getApplicationId());
        value.setPatientId(report.getPatientId());
        value.setReporterId(userId);
        criticalValueMapper.insert(value);
        log.info("[危急值] 上报: valueId={}, reportId={}, level={}, result={}",
                value.getId(), value.getReportId(), value.getCriticalLevel(), value.getResultValue());
        return getById(value.getId());
    }

    public CriticalValueVO getById(Long id) {
        CriticalValue value = criticalValueMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "危急值记录不存在");
        }
        CriticalValueVO vo = toVO(value);
        return vo;
    }

    public List<CriticalValueVO> list(String status, Long patientId) {
        return criticalValueMapper.selectList(status, patientId);
    }

    @Transactional(rollbackFor = Exception.class)
    public CriticalValueVO confirm(Long userId, Long id, String action, String comment) {
        CriticalValue value = criticalValueMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "危急值记录不存在");
        }
        if (!"PENDING".equals(value.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "危急值已复核");
        }
        String targetStatus = "CONFIRMED".equals(action) ? "CONFIRMED" : "RESOLVED";
        int rows = criticalValueMapper.confirm(id, targetStatus, userId, comment);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "危急值已被处理，请刷新");
        }
        log.info("[危急值] 复核: valueId={}, action={}, doctorId={}", id, action, userId);
        return getById(id);
    }

    private CriticalValueVO toVO(CriticalValue v) {
        CriticalValueVO vo = new CriticalValueVO();
        vo.setId(v.getId());
        vo.setReportId(v.getReportId());
        vo.setApplicationId(v.getApplicationId());
        vo.setPatientId(v.getPatientId());
        vo.setItemName(v.getItemName());
        vo.setResultValue(v.getResultValue());
        vo.setReferenceRange(v.getReferenceRange());
        vo.setCriticalLevel(v.getCriticalLevel());
        vo.setStatus(v.getStatus());
        vo.setReporterId(v.getReporterId());
        vo.setConfirmDoctorId(v.getConfirmDoctorId());
        vo.setConfirmComment(v.getConfirmComment());
        vo.setConfirmTime(v.getConfirmTime());
        vo.setCreateTime(v.getCreateTime());
        return vo;
    }
}
