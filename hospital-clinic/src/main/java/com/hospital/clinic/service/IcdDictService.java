package com.hospital.clinic.service;

import com.hospital.clinic.dto.IcdDictSaveDTO;
import com.hospital.clinic.entity.IcdDict;
import com.hospital.clinic.mapper.IcdDictMapper;
import com.hospital.clinic.vo.IcdDictVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ICD-10 诊断字典服务（迭代9 J3）
 * <p>
 * ① 管理端字典 CRUD + 分页查询（keyword 匹配 code/name、category 筛选、常用优先）；
 * ② 病历提交时校验诊断编码必须在字典内：
 *    {@link #validateCode} 为 fail-open 实现 —— 字典查询异常（如表缺失/DB 抖动）
 *    只记日志不抛错，不阻塞诊疗主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IcdDictService {

    private final IcdDictMapper icdDictMapper;

    /**
     * 分页查询（keyword 匹配 code/name、category 筛选、常用条目优先）
     */
    public Map<String, Object> page(String keyword, String category, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (no - 1) * size;

        List<IcdDictVO> records = icdDictMapper.selectPage(trimToNull(keyword), trimToNull(category), offset, size)
                .stream().map(this::toVO).collect(Collectors.toList());
        long total = icdDictMapper.countPage(trimToNull(keyword), trimToNull(category));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", no);
        result.put("pageSize", size);
        return result;
    }

    /** 字典详情 */
    public IcdDictVO getById(Long id) {
        IcdDict dict = icdDictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "ICD字典条目不存在");
        }
        return toVO(dict);
    }

    /** 新增字典条目（管理端） */
    public IcdDictVO create(IcdDictSaveDTO dto) {
        String code = dto.getIcdCode().trim();
        IcdDict existing = icdDictMapper.selectByCode(code);
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "ICD编码已存在: " + code);
        }
        IcdDict dict = new IcdDict();
        dict.setIcdCode(code);
        dict.setIcdName(dto.getIcdName().trim());
        dict.setCategory(trimToNull(dto.getCategory()));
        dict.setIsCommon(dto.getIsCommon() != null ? dto.getIsCommon() : 0);
        dict.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        icdDictMapper.insert(dict);
        log.info("[ICD字典] 新增: id={}, code={}, name={}", dict.getId(), dict.getIcdCode(), dict.getIcdName());
        return toVO(dict);
    }

    /** 编辑字典条目（管理端） */
    public IcdDictVO update(Long id, IcdDictSaveDTO dto) {
        IcdDict dict = icdDictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "ICD字典条目不存在");
        }
        String code = dto.getIcdCode().trim();
        IcdDict sameCode = icdDictMapper.selectByCode(code);
        if (sameCode != null && !sameCode.getId().equals(id)) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "ICD编码已被其他条目占用: " + code);
        }
        dict.setIcdCode(code);
        dict.setIcdName(dto.getIcdName().trim());
        dict.setCategory(trimToNull(dto.getCategory()));
        if (dto.getIsCommon() != null) {
            dict.setIsCommon(dto.getIsCommon());
        }
        if (dto.getStatus() != null) {
            dict.setStatus(dto.getStatus());
        }
        icdDictMapper.update(dict);
        log.info("[ICD字典] 更新: id={}, code={}", id, code);
        return toVO(dict);
    }

    /** 删除字典条目（管理端；medical_record.diagnosis_code 为自由文本引用，无外键） */
    public void delete(Long id) {
        IcdDict dict = icdDictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "ICD字典条目不存在");
        }
        icdDictMapper.deleteById(id);
        log.info("[ICD字典] 删除: id={}, code={}", id, dict.getIcdCode());
    }

    /** 章节分类列表（筛选下拉） */
    public List<String> categories() {
        return icdDictMapper.selectCategories();
    }

    /**
     * 校验诊断编码必须在启用的字典中存在（J3，fail-open）。
     * <p>
     * 编码未命中 → 抛 {@link BusinessException}；
     * 字典查询本身异常（DB 不可用等）→ 仅记录 warn 日志并放行，
     * 保证字典故障不阻塞病历提交主流程。
     *
     * @param diagnosisCode 病历诊断编码（调用方保证非空）
     */
    public void validateCode(String diagnosisCode) {
        try {
            long count = icdDictMapper.countEnabledByCode(diagnosisCode.trim());
            if (count <= 0) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "诊断编码不在 ICD-10 字典中: " + diagnosisCode);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // fail-open：字典查询异常不阻塞诊疗流程
            log.warn("[ICD字典] 字典校验异常，fail-open 放行: code={}, error={}", diagnosisCode, e.getMessage());
        }
    }

    private IcdDictVO toVO(IcdDict d) {
        return IcdDictVO.builder()
                .id(d.getId())
                .icdCode(d.getIcdCode())
                .icdName(d.getIcdName())
                .category(d.getCategory())
                .isCommon(d.getIsCommon())
                .status(d.getStatus())
                .createTime(d.getCreateTime())
                .build();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
