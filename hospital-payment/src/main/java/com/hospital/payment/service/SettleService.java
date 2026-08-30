package com.hospital.payment.service;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hospital.payment.entity.SettleRecord;
import com.hospital.payment.mapper.PaymentOrderMapper;
import com.hospital.payment.mapper.SettleRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收费员日结对账服务
 * <p>
 * 今日汇总（按订单类型/支付方式分组）与日结单生成（同一收费员 + 日期幂等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettleService {

    private final PaymentOrderMapper orderMapper;
    private final SettleRecordMapper settleRecordMapper;

    /**
     * 今日已支付订单汇总
     *
     * @return Map：date / totalAmount / orderCount / detail（分组明细列表）
     */
    public Map<String, Object> todaySummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<Map<String, Object>> detail = orderMapper.selectSettleSummary(start, end);

        BigDecimal totalAmount = BigDecimal.ZERO;
        int orderCount = 0;
        for (Map<String, Object> row : detail) {
            totalAmount = totalAmount.add(toBigDecimal(row.get("total_amount")));
            orderCount += toInt(row.get("order_count"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", today.toString());
        result.put("totalAmount", totalAmount);
        result.put("orderCount", orderCount);
        result.put("detail", detail);
        return result;
    }

    /**
     * 生成当日日结单（幂等）
     * <p>
     * 同一收费员 + 日期唯一：已存在则直接返回已有记录，否则汇总今日订单生成新日结单。
     *
     * @param cashierId 收费员 ID（auth userId）
     * @return 日结单
     */
    @Transactional(rollbackFor = Exception.class)
    public SettleRecord createSettle(Long cashierId) {
        LocalDate today = LocalDate.now();

        // 幂等：同一收费员 + 日期已存在则直接返回
        SettleRecord existing = settleRecordMapper.selectByCashierAndDate(cashierId, today);
        if (existing != null) {
            log.info("[日结] 已存在日结单，直接返回: settleNo={}", existing.getSettleNo());
            return existing;
        }

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<Map<String, Object>> detail = orderMapper.selectSettleSummary(start, end);

        BigDecimal totalAmount = BigDecimal.ZERO;
        int orderCount = 0;
        for (Map<String, Object> row : detail) {
            totalAmount = totalAmount.add(toBigDecimal(row.get("total_amount")));
            orderCount += toInt(row.get("order_count"));
        }

        SettleRecord record = new SettleRecord();
        record.setSettleNo(generateSettleNo());
        record.setCashierId(cashierId);
        record.setSettleDate(today);
        record.setTotalAmount(totalAmount);
        record.setOrderCount(orderCount);
        record.setDetail(JSONUtil.toJsonStr(detail));
        record.setCreateTime(LocalDateTime.now());
        settleRecordMapper.insert(record);
        log.info("[日结] 日结单已生成: settleNo={}, cashierId={}, totalAmount={}, orderCount={}",
                record.getSettleNo(), cashierId, totalAmount, orderCount);

        // 日结后打标（仅标记该收费员当日已支付且未结订单）
        int settled = orderMapper.markSettled(cashierId, start, end);
        log.info("[日结] 已打标订单数: {}", settled);

        return record;
    }

    // ==================== 私有方法 ====================

    private String generateSettleNo() {
        String random = UUID.fastUUID().toString().substring(0, 6).toUpperCase();
        return "STL" + System.currentTimeMillis() + random;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
