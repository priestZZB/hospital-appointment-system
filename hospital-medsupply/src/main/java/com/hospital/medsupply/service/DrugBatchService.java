package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.Drug;
import com.hospital.medsupply.entity.DrugBatch;
import com.hospital.medsupply.mapper.DrugBatchMapper;
import com.hospital.medsupply.mapper.DrugMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药库批次服务
 * <p>
 * 采购入库落批次并联动总库存；效期预警扫描 30 天内到期批次；
 * 报损清零批次并扣减总库存；麻精药品同步写五专登记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugBatchService {

    private final DrugBatchMapper batchMapper;
    private final DrugMapper drugMapper;
    private final InventoryService inventoryService;
    private final NarcoticService narcoticService;

    /**
     * 采购入库：插批次 + 总库存增加 + 麻精写 INBOUND 登记
     */
    @Transactional(rollbackFor = Exception.class)
    public DrugBatch purchaseInbound(DrugBatch batch, Long operatorId) {
        if (batch.getDrugId() == null || batch.getBatchNo() == null || batch.getBatchNo().isBlank()
                || batch.getExpiryDate() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "drugId、batchNo、expiryDate 不能为空");
        }
        if (batch.getQuantity() == null || batch.getQuantity() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "入库数量必须大于 0");
        }
        if (batch.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批次已过期，禁止入库");
        }
        Drug drug = drugMapper.selectById(batch.getDrugId());
        if (drug == null) {
            throw new BusinessException(ErrorCodeEnum.DRUG_NOT_FOUND);
        }

        batch.setInboundType("PURCHASE");
        batch.setStatus("ACTIVE");
        batch.setOperatorId(operatorId);
        batchMapper.insert(batch);
        log.info("[批次] 采购入库: batchId={}, drugId={}, batchNo={}, quantity={}",
                batch.getId(), batch.getDrugId(), batch.getBatchNo(), batch.getQuantity());

        // 联动总库存（含库存流水）
        inventoryService.inbound(batch.getDrugId(), batch.getQuantity(),
                "采购入库 batchNo=" + batch.getBatchNo(), operatorId);

        // 麻精药品五专登记
        registerIfControlled(drug, "INBOUND", batch.getQuantity(), null, null, operatorId,
                "采购入库 batchNo=" + batch.getBatchNo());
        return batchMapper.selectById(batch.getId());
    }

    /**
     * 效期预警：30 天内到期的在库批次
     */
    public List<DrugBatch> listExpiring() {
        return batchMapper.selectExpiring();
    }

    /**
     * 批次分页
     */
    public Map<String, Object> page(Long drugId, String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<DrugBatch> records = batchMapper.selectPage(drugId, status, offset, pageSize);
        long total = batchMapper.countPage(drugId, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 报损：批次清零标注 SCRAPPED + 总库存扣减 + 麻精写 SCRAP 登记
     */
    @Transactional(rollbackFor = Exception.class)
    public DrugBatch scrap(Long batchId, String reason, Long operatorId) {
        DrugBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "批次不存在");
        }
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅 ACTIVE 状态批次可报损");
        }
        int quantity = batch.getQuantity() == null ? 0 : batch.getQuantity();

        int rows = batchMapper.scrap(batchId);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "报损失败，批次状态已变更");
        }
        log.info("[批次] 报损: batchId={}, drugId={}, quantity={}, reason={}",
                batchId, batch.getDrugId(), quantity, reason);

        if (quantity > 0) {
            inventoryService.outbound(batch.getDrugId(), quantity,
                    "批次报损 batchId=" + batchId + (reason != null ? "：" + reason : ""), operatorId);
            Drug drug = drugMapper.selectById(batch.getDrugId());
            registerIfControlled(drug, "SCRAP", quantity, null, null, operatorId,
                    "批次报损 batchId=" + batchId + (reason != null ? "：" + reason : ""));
        }
        return batchMapper.selectById(batchId);
    }

    /** 管控药品（control_level != NORMAL）写五专登记 */
    private void registerIfControlled(Drug drug, String action, int quantity,
                                      Long prescriptionId, Long patientId,
                                      Long operatorId, String remark) {
        if (drug == null || drug.getControlLevel() == null || "NORMAL".equals(drug.getControlLevel())) {
            return;
        }
        narcoticService.register(drug.getId(), action, quantity, prescriptionId, patientId, operatorId, remark);
    }
}
