package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.DrugInventory;
import com.hospital.medsupply.entity.DrugInventoryLog;
import com.hospital.medsupply.mapper.DrugInventoryLogMapper;
import com.hospital.medsupply.mapper.DrugInventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final DrugInventoryMapper inventoryMapper;
    private final DrugInventoryLogMapper logMapper;

    public Map<String, Object> page(String keyword, Boolean lowStock, int pageNo, int pageSize) {
        // 简化实现：返回所有库存记录
        List<DrugInventory> list;
        if (Boolean.TRUE.equals(lowStock)) {
            list = inventoryMapper.selectLowStock();
        } else {
            list = List.of(); // 需要 JOIN drug 表做 keyword 搜索
        }
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", list.size());
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DrugInventory inbound(Long drugId, int quantity, String remark, Long operatorId) {
        DrugInventory inv = getOrCreate(drugId);
        int before = inv.getCurrentStock() == null ? 0 : inv.getCurrentStock();

        int rows = inventoryMapper.addStock(drugId, quantity, inv.getVersion());
        if (rows == 0) throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "入库失败，版本冲突");

        // 流水
        DrugInventoryLog log = new DrugInventoryLog();
        log.setDrugId(drugId);
        log.setChangeType("IN");
        log.setChangeQuantity(quantity);
        log.setBeforeStock(before);
        log.setAfterStock(before + quantity);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);

        return inventoryMapper.selectByDrugId(drugId);
    }

    @Transactional(rollbackFor = Exception.class)
    public DrugInventory outbound(Long drugId, int quantity, String remark, Long operatorId) {
        DrugInventory inv = getOrCreate(drugId);
        int before = inv.getCurrentStock() == null ? 0 : inv.getCurrentStock();

        int rows = inventoryMapper.deductStock(drugId, quantity, inv.getVersion());
        if (rows == 0) throw new BusinessException(ErrorCodeEnum.SLOT_NOT_ENOUGH, "库存不足或版本冲突");

        DrugInventoryLog log = new DrugInventoryLog();
        log.setDrugId(drugId);
        log.setChangeType("OUT");
        log.setChangeQuantity(-quantity);
        log.setBeforeStock(before);
        log.setAfterStock(before - quantity);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);

        return inventoryMapper.selectByDrugId(drugId);
    }

    @Transactional(rollbackFor = Exception.class)
    public DrugInventory adjust(Long drugId, int quantity, String remark, Long operatorId) {
        DrugInventory inv = getOrCreate(drugId);
        int before = inv.getCurrentStock() == null ? 0 : inv.getCurrentStock();
        int diff = quantity - before;

        if (diff > 0) {
            inventoryMapper.addStock(drugId, diff, inv.getVersion());
        } else if (diff < 0) {
            inventoryMapper.deductStock(drugId, -diff, inv.getVersion());
        } else {
            return inv;
        }

        DrugInventoryLog log = new DrugInventoryLog();
        log.setDrugId(drugId);
        log.setChangeType("ADJUST");
        log.setChangeQuantity(diff);
        log.setBeforeStock(before);
        log.setAfterStock(quantity);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);

        return inventoryMapper.selectByDrugId(drugId);
    }

    private DrugInventory getOrCreate(Long drugId) {
        DrugInventory inv = inventoryMapper.selectByDrugId(drugId);
        if (inv == null) {
            inv = new DrugInventory();
            inv.setDrugId(drugId);
            inv.setCurrentStock(0);
            inv.setVersion(0);
            inventoryMapper.insert(inv);
            inv = inventoryMapper.selectByDrugId(drugId);
        }
        return inv;
    }
}
