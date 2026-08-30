package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PrescriptionFeignClient;
import com.hospital.medsupply.entity.DrugDispense;
import com.hospital.medsupply.mapper.DrugDispenseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处方审核 / 发药确认服务
 * <p>
 * 审核与发药状态同时同步 clinic-service 的 prescription.status，
 * 发药时逐条乐观锁扣减药品库存并写流水。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispenseService {

    private final DrugDispenseMapper dispenseMapper;
    private final InventoryService inventoryService;
    private final PrescriptionFeignClient prescriptionFeignClient;

    /**
     * 处方审核（APPROVE 通过 / REJECT 驳回）
     */
    @Transactional(rollbackFor = Exception.class)
    public DrugDispense review(Long prescriptionId, String action, String reviewComment,
                               String reviewCheck, Long operatorId) {
        if (prescriptionId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "prescriptionId 不能为空");
        }
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "action 仅支持 APPROVE / REJECT");
        }

        // 审核前校验处方缴费状态（真实医院：划价收费 → 药师四查十对审核 → 发药）
        String payStatus = queryPayStatus(prescriptionId);
        if (!"PAID".equals(payStatus)) {
            throw new BusinessException(ErrorCodeEnum.PAY_NOT_COMPLETED, "处方尚未缴费，不可审核");
        }

        DrugDispense record = dispenseMapper.selectByPrescriptionId(prescriptionId);
        if (record == null) {
            record = new DrugDispense();
            record.setPrescriptionId(prescriptionId);
            record.setPatientId(resolvePatientId(prescriptionId));
            record.setStatus("PENDING_REVIEW");
            dispenseMapper.insert(record);
            log.info("[发药] 创建发药记录: prescriptionId={}, dispenseId={}", prescriptionId, record.getId());
        }
        if (!"PENDING_REVIEW".equals(record.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该处方已审核，请勿重复操作");
        }

        String target = "APPROVE".equals(action) ? "REVIEW_PASSED" : "REVIEW_REJECTED";
        int rows = dispenseMapper.updateReview(record.getId(), target, reviewComment, reviewCheck, operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "处方审核失败，状态已变更");
        }

        try {
            prescriptionFeignClient.updateStatus(prescriptionId, target, "PENDING_REVIEW", reviewComment);
        } catch (Exception e) {
            log.warn("[发药] 同步 clinic 处方状态失败（忽略）: prescriptionId={}, error={}", prescriptionId, e.getMessage());
        }
        return dispenseMapper.selectById(record.getId());
    }

    /**
     * 发药确认（乐观锁逐条扣库存 + 写流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public DrugDispense dispense(Long prescriptionId, Long operatorId) {
        DrugDispense record = dispenseMapper.selectByPrescriptionId(prescriptionId);
        if (record == null || !"REVIEW_PASSED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅审核通过的处方可发药");
        }

        // 发药前校验处方缴费状态（处方在 clinic 服务，只能 Feign 查询）
        String payStatus = queryPayStatus(prescriptionId);
        if (!"PAID".equals(payStatus)) {
            throw new BusinessException(ErrorCodeEnum.PAY_NOT_COMPLETED, "处方尚未缴费，不可发药");
        }

        List<Map<String, Object>> items = prescriptionFeignClient.getItems(prescriptionId);
        if (items != null) {
            for (Map<String, Object> item : items) {
                Object drugId = item.get("drugId");
                Object quantity = item.get("quantity");
                if (drugId == null || quantity == null) {
                    continue;
                }
                int qty = ((Number) quantity).intValue();
                if (qty <= 0) {
                    continue;
                }
                inventoryService.outbound(((Number) drugId).longValue(), qty,
                        "处方发药 prescriptionId=" + prescriptionId, operatorId);
            }
        }

        int rows = dispenseMapper.updateDispensed(record.getId(), "DISPENSED", operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "发药失败，状态已变更");
        }
        try {
            prescriptionFeignClient.updateStatus(prescriptionId, "DISPENSED", "REVIEW_PASSED", null);
        } catch (Exception e) {
            log.warn("[发药] 同步 clinic 处方状态失败（忽略）: prescriptionId={}, error={}", prescriptionId, e.getMessage());
        }
        return dispenseMapper.selectById(record.getId());
    }

    /**
     * 发药记录分页（按状态筛选）
     */
    public Map<String, Object> page(String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<DrugDispense> list = dispenseMapper.selectPageByStatus(status, offset, pageSize);
        long total = dispenseMapper.countByStatus(status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    private Long resolvePatientId(Long prescriptionId) {
        try {
            List<Map<String, Object>> items = prescriptionFeignClient.getItems(prescriptionId);
            if (items != null && !items.isEmpty() && items.get(0).get("patientId") != null) {
                return ((Number) items.get(0).get("patientId")).longValue();
            }
        } catch (Exception e) {
            log.warn("[发药] 查询处方患者失败（忽略）: prescriptionId={}, error={}", prescriptionId, e.getMessage());
        }
        return null;
    }

    private String queryPayStatus(Long prescriptionId) {
        try {
            Map<String, Object> result = prescriptionFeignClient.getPayStatus(prescriptionId);
            if (result != null && result.get("payStatus") != null) {
                return String.valueOf(result.get("payStatus"));
            }
        } catch (Exception e) {
            log.warn("[发药] 查询处方缴费状态失败: prescriptionId={}, error={}", prescriptionId, e.getMessage());
        }
        return null;
    }
}
