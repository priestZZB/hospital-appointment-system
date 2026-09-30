package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.Drug;
import com.hospital.medsupply.mapper.DrugMapper;
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
public class DrugService {

    private final DrugMapper drugMapper;

    public Map<String, Object> page(String keyword, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<Drug> list = drugMapper.selectPage(keyword, offset, pageSize);
        long total = drugMapper.count(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    public Drug getById(Long id) {
        Drug d = drugMapper.selectById(id);
        if (d == null) throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "药品不存在");
        return d;
    }

    @Transactional(rollbackFor = Exception.class)
    public Drug create(Drug drug) {
        if (drugMapper.selectByCode(drug.getDrugCode()) != null)
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "药品编码已存在");
        drug.setStatus(1);
        drugMapper.insert(drug);
        return drug;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Drug drug) {
        if (drugMapper.selectById(drug.getId()) == null)
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "药品不存在");
        drugMapper.update(drug);
    }

    /**
     * 药品三分类/管控级别/抗菌分级管理（V8 B1/B8/B10）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, String drugType, String controlLevel, String antibioticLevel) {
        if (drugMapper.selectById(id) == null)
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "药品不存在");
        if (drugType == null || drugType.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "drugType 不能为空");
        }
        drugMapper.updateDrugType(id, drugType, controlLevel, antibioticLevel);
    }
}
