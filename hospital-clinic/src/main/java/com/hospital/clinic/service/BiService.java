package com.hospital.clinic.service;

import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.vo.BiOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BI 统计服务
 * <p>
 * 当日概览、近 7 日趋势、科室占比统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiService {

    private final AppointmentMapper appointmentMapper;

    /**
     * BI 统计概览
     */
    public BiOverviewVO getOverview() {
        LocalDate today = LocalDate.now();

        // 当日挂号量（所有已创建预约）
        Long todayAppointments = appointmentMapper.countByDate(today);

        // 当日就诊量（visit_status = COMPLETED）
        Long todayConsultations = appointmentMapper.countCompletedByDate(today);

        // 当日收入（PAID 状态）
        BigDecimal todayRevenue = appointmentMapper.sumRevenueByDate(today);
        if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;

        // 近 7 日趋势
        List<Map<String, Object>> weeklyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Long count = appointmentMapper.countByDate(date);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            item.put("count", count != null ? count : 0);
            weeklyTrend.add(item);
        }

        // 科室占比
        List<Map<String, Object>> deptDistribution = appointmentMapper.countGroupByDept(today);
        if (deptDistribution == null) deptDistribution = new ArrayList<>();

        return BiOverviewVO.builder()
                .todayAppointments(todayAppointments != null ? todayAppointments : 0)
                .todayConsultations(todayConsultations != null ? todayConsultations : 0)
                .todayRevenue(todayRevenue)
                .weeklyTrend(weeklyTrend)
                .deptDistribution(deptDistribution)
                .build();
    }
}
