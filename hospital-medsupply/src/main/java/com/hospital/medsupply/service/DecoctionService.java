package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.DecoctionOrder;
import com.hospital.medsupply.mapper.DecoctionOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 中药代煎订单服务
 * <p>
 * 处方为中药饮片（HERBAL）时可下单：SELF 自煎无凭证、HOSPITAL 代煎生成
 * 6 位取药凭证码并按剂数计代煎费（3.00 元/剂）。
 * 状态机：PENDING → DECOCTING → READY → DISPENSED，CANCELLED 可从任意前置态取消。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecoctionService {

    private static final Set<String> TYPES = Set.of("SELF", "HOSPITAL");
    /** 前向流转：目标状态 → 期望当前状态 */
    private static final Map<String, String> FORWARD = Map.of(
            "DECOCTING", "PENDING",
            "READY", "DECOCTING",
            "DISPENSED", "READY");
    /** 可取消的前置状态 */
    private static final Set<String> CANCELLABLE = Set.of("PENDING", "DECOCTING", "READY");

    /** 代煎费单价（元/剂） */
    private static final BigDecimal FEE_PER_DOSE = new BigDecimal("3.00");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DecoctionOrderMapper decoctionOrderMapper;

    /**
     * 下单（处方 HERBAL 时）
     */
    @Transactional(rollbackFor = Exception.class)
    public DecoctionOrder create(DecoctionOrder order) {
        if (order.getPrescriptionId() == null || order.getPatientId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "prescriptionId、patientId 不能为空");
        }
        if (order.getDecoctionType() == null || !TYPES.contains(order.getDecoctionType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "decoctionType 仅支持 SELF / HOSPITAL");
        }
        int doses = order.getDoses() != null ? order.getDoses() : 7;
        if (doses <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "剂数必须大于 0");
        }
        order.setDoses(doses);
        order.setOrderNo("DC" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setStatus("PENDING");
        if ("HOSPITAL".equals(order.getDecoctionType())) {
            order.setPickupCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
            order.setFeeAmount(FEE_PER_DOSE.multiply(BigDecimal.valueOf(doses)));
        } else {
            order.setPickupCode(null);
            order.setFeeAmount(BigDecimal.ZERO);
        }
        decoctionOrderMapper.insert(order);
        log.info("[代煎] 下单: orderId={}, prescriptionId={}, type={}, doses={}, fee={}, pickupCode={}",
                order.getId(), order.getPrescriptionId(), order.getDecoctionType(), doses,
                order.getFeeAmount(), order.getPickupCode() != null ? "已生成" : "无");
        return decoctionOrderMapper.selectById(order.getId());
    }

    /**
     * 状态流转（药师）
     * <p>
     * PENDING → DECOCTING → READY → DISPENSED；CANCELLED 可从任意前置态取消；
     * 非法流转抛 PARAM_ERROR。
     */
    @Transactional(rollbackFor = Exception.class)
    public DecoctionOrder handle(Long id, String action) {
        if (action == null || action.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "action 不能为空");
        }
        DecoctionOrder order = decoctionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "代煎订单不存在");
        }
        String expected;
        if ("CANCELLED".equals(action)) {
            if (!CANCELLABLE.contains(order.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "当前状态 " + order.getStatus() + " 不可取消");
            }
            expected = order.getStatus();
        } else {
            expected = FORWARD.get(action);
            if (expected == null || !expected.equals(order.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "非法状态流转: " + order.getStatus() + " → " + action);
            }
        }
        int rows = decoctionOrderMapper.updateStatus(id, action, expected);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态已变更，请刷新后重试");
        }
        log.info("[代煎] 状态流转: orderId={}, {} → {}", id, expected, action);
        return decoctionOrderMapper.selectById(id);
    }

    /**
     * 患者本人订单分页
     */
    public Map<String, Object> my(Long patientId, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<DecoctionOrder> records = decoctionOrderMapper.selectByPatient(patientId, offset, pageSize);
        long total = decoctionOrderMapper.countByPatient(patientId);
        return page(records, total, pageNo, pageSize);
    }

    /**
     * 药师分页
     */
    public Map<String, Object> page(String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<DecoctionOrder> records = decoctionOrderMapper.selectByPage(status, offset, pageSize);
        long total = decoctionOrderMapper.countByPage(status);
        return page(records, total, pageNo, pageSize);
    }

    private Map<String, Object> page(List<DecoctionOrder> records, long total, int pageNo, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }
}
