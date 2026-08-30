package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.realtime.RealtimePublisher;
import com.hospital.medsupply.entity.InfusionOrder;
import com.hospital.medsupply.entity.InfusionRecord;
import com.hospital.medsupply.mapper.InfusionOrderMapper;
import com.hospital.medsupply.mapper.InfusionRecordMapper;
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
 * 输液服务
 * <p>
 * 医生开输液医嘱，护士站执行输液并记录执行过程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfusionService {

    private final InfusionOrderMapper infusionOrderMapper;
    private final InfusionRecordMapper infusionRecordMapper;
    private final RealtimePublisher realtimePublisher;

    /**
     * 医生开输液医嘱
     * <p>
     * 生成输液单编号（INF + UUID 前 20 位大写），
     * 初始 payStatus=UNPAID、status=PENDING，总金额 = 单价 × 天数。
     */
    @Transactional(rollbackFor = Exception.class)
    public InfusionOrder createOrder(InfusionOrder order) {
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "输液单参数不能为空");
        }
        order.setInfusionNo("INF" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 20).toUpperCase());
        order.setPayStatus("UNPAID");
        order.setStatus("PENDING");
        BigDecimal unitPrice = order.getUnitPrice() == null ? BigDecimal.ZERO : order.getUnitPrice();
        int days = order.getDays() == null ? 1 : order.getDays();
        order.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(days)));
        infusionOrderMapper.insert(order);
        log.info("[输液] 输液单创建: infusionNo={}, patientId={}, totalAmount={}",
                order.getInfusionNo(), order.getPatientId(), order.getTotalAmount());
        return order;
    }

    /** 患者分页查询本人输液单 */
    public Map<String, Object> listByPatient(Long patientId, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<InfusionOrder> list = infusionOrderMapper.selectByPatientId(patientId, offset, pageSize);
        long total = infusionOrderMapper.countByPatientId(patientId);
        return pageResult(list, total, pageNo, pageSize);
    }

    /** 护士站待执行列表（status=PENDING，已缴费优先） */
    public Map<String, Object> listPending(int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<InfusionOrder> list = infusionOrderMapper.selectByStatus("PENDING", offset, pageSize);
        long total = infusionOrderMapper.countByStatus("PENDING");
        return pageResult(list, total, pageNo, pageSize);
    }

    /** 按执行状态分页查询 */
    public Map<String, Object> listByStatus(String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<InfusionOrder> list = infusionOrderMapper.selectByStatus(status, offset, pageSize);
        long total = infusionOrderMapper.countByStatus(status);
        return pageResult(list, total, pageNo, pageSize);
    }

    /**
     * 护士执行记录
     * <p>
     * START 时执行状态 → IN_PROGRESS；END 时执行状态 → COMPLETED。
     */
    @Transactional(rollbackFor = Exception.class)
    public InfusionRecord executeRecord(Long infusionOrderId, String recordType, String content,
                                        String skinTestResult, Integer dropRate,
                                        Long operatorId, String operatorName) {
        if (infusionOrderId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "infusionOrderId 不能为空");
        }
        if (recordType == null || recordType.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "recordType 不能为空");
        }
        InfusionOrder order = infusionOrderMapper.selectById(infusionOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "输液单不存在");
        }
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BusinessException(ErrorCodeEnum.PAY_NOT_COMPLETED, "输液单尚未缴费，不可执行");
        }

        InfusionRecord record = new InfusionRecord();
        record.setInfusionOrderId(infusionOrderId);
        record.setRecordType(recordType);
        record.setRecordContent(content);
        record.setSkinTestResult(skinTestResult);
        record.setDropRate(dropRate);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        infusionRecordMapper.insert(record);

        if ("START".equals(recordType)) {
            infusionOrderMapper.updateStatus(infusionOrderId, "IN_PROGRESS");
        } else if ("END".equals(recordType)) {
            infusionOrderMapper.updateStatus(infusionOrderId, "COMPLETED");
            // 输液完成 → 跨服务实时推送（clinic WebSocket 桥接 → 患者端订阅 /topic/report/{patientId}）
            realtimePublisher.publish("/topic/report/" + order.getPatientId(), "INFUSION_COMPLETED",
                    Map.of("infusionOrderId", infusionOrderId, "patientId", order.getPatientId(),
                            "infusionNo", order.getInfusionNo()));
        }
        log.info("[输液] 执行记录: orderId={}, recordType={}, operatorId={}",
                infusionOrderId, recordType, operatorId);
        return record;
    }

    /** 查询输液单详情 */
    public InfusionOrder getById(Long id) {
        return infusionOrderMapper.selectById(id);
    }

    private Map<String, Object> pageResult(List<InfusionOrder> list, long total, int pageNo, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }
}
