package com.hospital.payment.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.payment.entity.ChargeItem;
import com.hospital.payment.entity.ChargeItemAdjustLog;
import com.hospital.payment.mapper.ChargeItemAdjustLogMapper;
import com.hospital.payment.mapper.ChargeItemMapper;
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
 * 统一收费项目目录服务（迭代11 H4）。
 * <p>
 * 目录为自费/医保结算共用的价格口径：编码唯一、调价同事务写历史、
 * price_status 状态机 ACTIVE --调价--> ADJUSTED，启用/停用切换 ACTIVE/DEPRECATED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeItemService {

    /** 合法类别 */
    private static final Set<String> CATEGORIES = Set.of("DIAGNOSIS", "MATERIAL", "SERVICE", "EXAM", "LAB");

    /** 启用/停用允许落入的价格状态（ADJUSTED 仅由调价动作产生） */
    private static final Set<String> TOGGLE_STATUS = Set.of("ACTIVE", "DEPRECATED");

    private final ChargeItemMapper chargeItemMapper;
    private final ChargeItemAdjustLogMapper adjustLogMapper;

    /**
     * 新增收费项目（item_code 唯一）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeItem create(ChargeItem item) {
        validateCommon(item, true);
        ChargeItem existing = chargeItemMapper.selectByItemCode(item.getItemCode());
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "收费项目编码已存在: " + item.getItemCode());
        }
        chargeItemMapper.insert(item);
        log.info("[收费目录] 新增收费项目: id={}, code={}, name={}, price={}",
                item.getId(), item.getItemCode(), item.getItemName(), item.getUnitPrice());
        return chargeItemMapper.selectById(item.getId());
    }

    /**
     * 编辑收费项目（名称/类别/单位/单价/说明；item_code 不可改，改价请走调价接口留痕）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeItem update(Long id, ChargeItem item) {
        ChargeItem existing = requireExists(id);
        validateCommon(item, false);
        // 编辑也允许直接改价，但视为调价留痕（与 adjustPrice 同口径，避免无痕改价）
        boolean priceChanged = item.getUnitPrice() != null
                && existing.getUnitPrice() != null
                && item.getUnitPrice().compareTo(existing.getUnitPrice()) != 0;
        chargeItemMapper.update(item);
        if (priceChanged) {
            insertAdjustLog(id, existing.getUnitPrice(), item.getUnitPrice(),
                    "编辑收费项目时改价", UserContext.getUserId());
            chargeItemMapper.adjustPrice(id, item.getUnitPrice(), "编辑收费项目时改价");
        }
        log.info("[收费目录] 编辑收费项目: id={}, code={}, priceChanged={}",
                id, existing.getItemCode(), priceChanged);
        return chargeItemMapper.selectById(id);
    }

    /**
     * 调价：更新 unit_price + price_status=ADJUSTED + adjust_note，同事务写 adjust_log
     *
     * @param id       收费项目 ID
     * @param newPrice 新单价
     * @param reason   调价原因
     * @return 调价后的收费项目
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeItem adjustPrice(Long id, BigDecimal newPrice, String reason) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "新单价必须大于 0");
        }
        ChargeItem existing = requireExists(id);
        if (existing.getUnitPrice() != null && existing.getUnitPrice().compareTo(newPrice) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "新单价与现价一致，无需调价");
        }
        insertAdjustLog(id, existing.getUnitPrice(), newPrice, reason, UserContext.getUserId());
        chargeItemMapper.adjustPrice(id, newPrice, reason);
        log.info("[收费目录] 调价: id={}, old={}, new={}, reason={}",
                id, existing.getUnitPrice(), newPrice, reason);
        return chargeItemMapper.selectById(id);
    }

    /**
     * 启用/停用（ACTIVE/DEPRECATED）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargeItem updateStatus(Long id, String status) {
        if (status == null || !TOGGLE_STATUS.contains(status)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "status 仅允许 ACTIVE/DEPRECATED");
        }
        requireExists(id);
        chargeItemMapper.updateStatus(id, status);
        log.info("[收费目录] 启用/停用: id={}, status={}", id, status);
        return chargeItemMapper.selectById(id);
    }

    /**
     * 分页查询
     */
    public Map<String, Object> page(String category, String keyword, Integer pageNo, Integer pageSize) {
        int no = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        int offset = (no - 1) * size;
        long total = chargeItemMapper.countPage(category, keyword);
        List<ChargeItem> list = chargeItemMapper.selectPage(category, keyword, offset, size);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("pageNo", no);
        result.put("pageSize", size);
        result.put("list", list);
        return result;
    }

    // ==================== 私有方法 ====================

    private ChargeItem requireExists(Long id) {
        ChargeItem existing = chargeItemMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "收费项目不存在");
        }
        return existing;
    }

    private void validateCommon(ChargeItem item, boolean withCode) {
        if (withCode && (item.getItemCode() == null || item.getItemCode().isBlank())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "缺少项目编码 itemCode");
        }
        if (item.getItemName() == null || item.getItemName().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "缺少项目名称 itemName");
        }
        if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "单价必须大于 0");
        }
        if (item.getCategory() != null && !item.getCategory().isBlank() && !CATEGORIES.contains(item.getCategory())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "category 仅允许 DIAGNOSIS/MATERIAL/SERVICE/EXAM/LAB");
        }
    }

    private void insertAdjustLog(Long itemId, BigDecimal oldPrice, BigDecimal newPrice, String reason, Long operatorId) {
        ChargeItemAdjustLog logEntry = new ChargeItemAdjustLog();
        logEntry.setItemId(itemId);
        logEntry.setOldPrice(oldPrice);
        logEntry.setNewPrice(newPrice);
        logEntry.setReason(reason);
        logEntry.setOperatorId(operatorId);
        adjustLogMapper.insert(logEntry);
    }
}
