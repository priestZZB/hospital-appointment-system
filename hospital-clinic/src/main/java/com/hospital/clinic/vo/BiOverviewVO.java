package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BI 统计概览 VO
 */
@Data
@Builder
public class BiOverviewVO {

    /** 当日挂号量 */
    private Long todayAppointments;

    /** 当日就诊量 */
    private Long todayConsultations;

    /** 当日收入 */
    private BigDecimal todayRevenue;

    /** 近7日趋势（日期 → 挂号量） */
    private List<Map<String, Object>> weeklyTrend;

    /** 科室占比（科室名 → 挂号量） */
    private List<Map<String, Object>> deptDistribution;
}
