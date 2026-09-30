package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.Drug;
import com.hospital.medsupply.entity.DrugBatch;
import com.hospital.medsupply.entity.DrugDispense;
import com.hospital.medsupply.entity.DrugReturn;
import com.hospital.medsupply.mapper.DrugBatchMapper;
import com.hospital.medsupply.mapper.DrugDispenseMapper;
import com.hospital.medsupply.mapper.DrugMapper;
import com.hospital.medsupply.mapper.DrugReturnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 退药服务
 * <p>
 * 发药后退药冲账：校验处方已发药 → 写退药单 → 总库存回冲 + 批次回冲
 * （inbound_type=RETURN 记录）→ 麻精药品写 RETURN 五专登记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugReturnService {

    private final DrugReturnMapper returnMapper;
    private final DrugDispenseMapper dispenseMapper;
    private final DrugMapper drugMapper;
    private final DrugBatchMapper batchMapper;
    private final InventoryService inventoryService;
    private final NarcoticService narcoticService;

    /**
     * 退药冲账
     */
    @Transactional(rollbackFor = Exception.class)
    public DrugReturn create(DrugReturn drugReturn, Long operatorId) {
        if (drugReturn.getPrescriptionId() == null || drugReturn.getPatientId() == null
                || drugReturn.getDrugId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "prescriptionId、patientId、drugId 不能为空");
        }
        if (drugReturn.getQuantity() == null || drugReturn.getQuantity() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "退药数量必须大于 0");
        }

        // 校验该处方已发药
        DrugDispense dispense = dispenseMapper.selectByPrescriptionId(drugReturn.getPrescriptionId());
        if (dispense == null || !"DISPENSED".equals(dispense.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该处方尚未发药，不可退药");
        }

        Drug drug = drugMapper.selectById(drugReturn.getDrugId());
        if (drug == null) {
            throw new BusinessException(ErrorCodeEnum.DRUG_NOT_FOUND);
        }

        // 金额冲账：按参考价计算
        BigDecimal unitPrice = drug.getReferencePrice() != null ? drug.getReferencePrice() : BigDecimal.ZERO;
        drugReturn.setRefundAmount(unitPrice.multiply(BigDecimal.valueOf(drugReturn.getQuantity())));
        drugReturn.setReturnNo("RT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        drugReturn.setStatus("COMPLETED");
        drugReturn.setOperatorId(operatorId);
        returnMapper.insert(drugReturn);
        log.info("[退药] 冲账: returnId={}, prescriptionId={}, drugId={}, quantity={}, refund={}",
                drugReturn.getId(), drugReturn.getPrescriptionId(), drugReturn.getDrugId(),
                drugReturn.getQuantity(), drugReturn.getRefundAmount());

        // 总库存回冲（含库存流水）
        inventoryService.inbound(drugReturn.getDrugId(), drugReturn.getQuantity(),
                "退药回冲 returnNo=" + drugReturn.getReturnNo(), operatorId);

        // 批次回冲：以 RETURN 类型重新入库，批次号/效期沿用最新在库批次
        returnInbound(drugReturn, operatorId);

        // 麻精药品五专登记
        if (drug.getControlLevel() != null && !"NORMAL".equals(drug.getControlLevel())) {
            narcoticService.register(drug.getId(), "RETURN", drugReturn.getQuantity(),
                    drugReturn.getPrescriptionId(), drugReturn.getPatientId(), operatorId,
                    "退药回冲 returnNo=" + drugReturn.getReturnNo());
        }
        return drugReturn;
    }

    /**
     * 批次回冲：插入 inbound_type=RETURN 的批次记录（数量为正）
     */
    private void returnInbound(DrugReturn drugReturn, Long operatorId) {
        DrugBatch latest = batchMapper.selectLatestActiveByDrugId(drugReturn.getDrugId());
        DrugBatch batch = new DrugBatch();
        batch.setDrugId(drugReturn.getDrugId());
        batch.setBatchNo(latest != null ? latest.getBatchNo()
                : "RETURN" + drugReturn.getId());
        batch.setSupplier("退药回冲 " + drugReturn.getReturnNo());
        batch.setQuantity(drugReturn.getQuantity());
        batch.setExpiryDate(latest != null && latest.getExpiryDate() != null
                ? latest.getExpiryDate() : java.time.LocalDate.now().plusYears(1));
        batch.setInboundType("RETURN");
        batch.setStatus("ACTIVE");
        batch.setOperatorId(operatorId);
        batchMapper.insert(batch);
    }

    /**
     * 退药单分页
     */
    public Map<String, Object> page(Long patientId, String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<DrugReturn> records = returnMapper.selectPage(patientId, status, offset, pageSize);
        long total = returnMapper.countPage(patientId, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }
}
