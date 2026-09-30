package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.DrugTransfer;
import com.hospital.medsupply.mapper.DrugTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 药品调拨服务
 * <p>
 * 药库 → 药房 → 科室流转留痕。简化模式：总库存不变、批次不拆分，
 * 仅记录调拨单（from/to 位置由业务语义约定）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugTransferService {

    /** 合法位置：药库 / 药房 / 临床科室 */
    private static final Set<String> LOCATIONS = Set.of("WAREHOUSE", "PHARMACY", "DEPT");

    private final DrugTransferMapper transferMapper;

    /**
     * 创建调拨单
     */
    @Transactional(rollbackFor = Exception.class)
    public DrugTransfer create(DrugTransfer transfer, Long operatorId) {
        if (transfer.getDrugId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "drugId 不能为空");
        }
        if (transfer.getQuantity() == null || transfer.getQuantity() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "调拨数量必须大于 0");
        }
        if (!LOCATIONS.contains(transfer.getFromLocation()) || !LOCATIONS.contains(transfer.getToLocation())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "from/to 仅支持 WAREHOUSE / PHARMACY / DEPT");
        }
        if (transfer.getFromLocation().equals(transfer.getToLocation())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "调出与调入位置不能相同");
        }

        transfer.setTransferNo("TF" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        transfer.setStatus("COMPLETED");
        transfer.setOperatorId(operatorId);
        transferMapper.insert(transfer);
        log.info("[调拨] 创建调拨单: transferId={}, drugId={}, quantity={}, {} → {}",
                transfer.getId(), transfer.getDrugId(), transfer.getQuantity(),
                transfer.getFromLocation(), transfer.getToLocation());
        return transfer;
    }

    /**
     * 调拨单分页
     */
    public Map<String, Object> page(Long drugId, String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<DrugTransfer> records = transferMapper.selectPage(drugId, status, offset, pageSize);
        long total = transferMapper.countPage(drugId, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }
}
