package com.hospital.payment.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.payment.entity.InsuranceCatalog;
import com.hospital.payment.mapper.InsuranceCatalogMapper;
import com.hospital.payment.vo.InsuranceCatalogResolveVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医保目录映射服务（迭代11 H1）。
 * <p>
 * (item_type, item_ref_id) 唯一（uk_ins_cat）：POST 录入遇 UNIQUE 冲突改为更新（upsert）；
 * resolve 供医保结算逐项解析目录分类，未配置映射默认自费（C / 0%）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceCatalogService {

    /** 业务类型 */
    private static final Set<String> ITEM_TYPES =
            Set.of("REGISTER", "DRUG", "EXAM", "LAB", "TREATMENT", "MATERIAL", "CHARGED_ITEM");

    /** 目录分类 */
    private static final Set<String> CLASSES = Set.of("A", "B", "C");

    private final InsuranceCatalogMapper catalogMapper;

    /**
     * 录入映射（UNIQUE 冲突改更新：同 (itemType, refId) 已配置则覆盖目录分类/比例等）
     */
    @Transactional(rollbackFor = Exception.class)
    public InsuranceCatalog save(InsuranceCatalog catalog) {
        validate(catalog);
        InsuranceCatalog existing = catalogMapper.selectByTypeAndRef(catalog.getItemType(), catalog.getItemRefId());
        if (existing != null) {
            existing.setItemName(catalog.getItemName());
            existing.setCatalogClass(catalog.getCatalogClass());
            existing.setReimburseRatio(catalog.getReimburseRatio());
            existing.setStatus(catalog.getStatus());
            catalogMapper.update(existing);
            log.info("[医保目录] 映射已存在，更新覆盖: id={}, type={}, refId={}, class={}",
                    existing.getId(), existing.getItemType(), existing.getItemRefId(), existing.getCatalogClass());
            return catalogMapper.selectById(existing.getId());
        }
        catalogMapper.insert(catalog);
        log.info("[医保目录] 新增映射: id={}, type={}, refId={}, class={}, ratio={}",
                catalog.getId(), catalog.getItemType(), catalog.getItemRefId(),
                catalog.getCatalogClass(), catalog.getReimburseRatio());
        return catalogMapper.selectById(catalog.getId());
    }

    /**
     * 编辑映射（item_type/item_ref_id 为唯一键不随编辑变化）
     */
    @Transactional(rollbackFor = Exception.class)
    public InsuranceCatalog update(Long id, InsuranceCatalog catalog) {
        InsuranceCatalog existing = requireExists(id);
        if (catalog.getItemName() == null && catalog.getCatalogClass() == null
                && catalog.getReimburseRatio() == null && catalog.getStatus() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "无可更新字段");
        }
        existing.setItemName(catalog.getItemName() != null ? catalog.getItemName() : existing.getItemName());
        if (catalog.getCatalogClass() != null) {
            if (!CLASSES.contains(catalog.getCatalogClass())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "catalogClass 仅允许 A/B/C");
            }
            existing.setCatalogClass(catalog.getCatalogClass());
        }
        if (catalog.getReimburseRatio() != null) {
            existing.setReimburseRatio(catalog.getReimburseRatio());
        }
        if (catalog.getStatus() != null) {
            existing.setStatus(catalog.getStatus());
        }
        catalogMapper.update(existing);
        log.info("[医保目录] 编辑映射: id={}, class={}, ratio={}, status={}",
                id, existing.getCatalogClass(), existing.getReimburseRatio(), existing.getStatus());
        return catalogMapper.selectById(id);
    }

    /**
     * 分页查询
     */
    public Map<String, Object> page(String itemType, String catalogClass, Integer pageNo, Integer pageSize) {
        int no = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        int offset = (no - 1) * size;
        long total = catalogMapper.countPage(itemType, catalogClass);
        List<InsuranceCatalog> list = catalogMapper.selectPage(itemType, catalogClass, offset, size);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("pageNo", no);
        result.put("pageSize", size);
        result.put("list", list);
        return result;
    }

    /**
     * 目录解析：已配置返回映射内容；未配置返回默认自费（catalogClass=C、reimburseRatio=0）
     */
    public InsuranceCatalogResolveVO resolve(String itemType, Long refId) {
        if (itemType == null || itemType.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "缺少 itemType");
        }
        if (refId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "缺少 refId");
        }
        InsuranceCatalog catalog = catalogMapper.selectByTypeAndRef(itemType, refId);
        if (catalog != null) {
            return InsuranceCatalogResolveVO.builder()
                    .itemType(catalog.getItemType())
                    .refId(catalog.getItemRefId())
                    .itemName(catalog.getItemName())
                    .catalogClass(catalog.getCatalogClass())
                    .reimburseRatio(catalog.getReimburseRatio())
                    .configured(true)
                    .build();
        }
        return InsuranceCatalogResolveVO.builder()
                .itemType(itemType)
                .refId(refId)
                .itemName(null)
                .catalogClass("C")
                .reimburseRatio(BigDecimal.ZERO)
                .configured(false)
                .build();
    }

    // ==================== 私有方法 ====================

    private InsuranceCatalog requireExists(Long id) {
        InsuranceCatalog existing = catalogMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "医保目录映射不存在");
        }
        return existing;
    }

    private void validate(InsuranceCatalog catalog) {
        if (catalog.getItemType() == null || !ITEM_TYPES.contains(catalog.getItemType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "itemType 仅允许 REGISTER/DRUG/EXAM/LAB/TREATMENT/MATERIAL/CHARGED_ITEM");
        }
        if (catalog.getCatalogClass() == null || !CLASSES.contains(catalog.getCatalogClass())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "catalogClass 仅允许 A/B/C");
        }
        // 未显式给比例时按分类给默认口径：甲类 100.00、乙类 15.00、自费 0
        if (catalog.getReimburseRatio() == null) {
            catalog.setReimburseRatio(defaultRatio(catalog.getCatalogClass()));
        }
    }

    private BigDecimal defaultRatio(String catalogClass) {
        return switch (catalogClass) {
            case "A" -> new BigDecimal("100.00");
            case "B" -> new BigDecimal("15.00");
            default -> BigDecimal.ZERO;
        };
    }
}
