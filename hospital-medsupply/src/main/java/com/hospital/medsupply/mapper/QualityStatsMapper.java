package com.hospital.medsupply.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 医疗质量指标 Mapper（迭代12补全 J2 质控看板）。
 * 纯 @Select 聚合查询：危急值闭环率、报告完成率（及时率）、平均报告时长。
 */
@Mapper
public interface QualityStatsMapper {

    /** 危急值闭环率：CONFIRMED（已复核确认）/ 总数 */
    @Select("SELECT COUNT(*) AS \"total\", " +
            "  NVL(SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS \"closed\" " +
            "FROM critical_value " +
            "WHERE (#{date} IS NULL OR TRUNC(create_time) = TRUNC(TO_DATE(#{date}, 'YYYY-MM-DD')))")
    Map<String, Object> criticalSummary(@Param("date") String date);

    /** 报告完成率（及时率）：complete_time 非空比例 + 平均报告时长（小时） */
    @Select("SELECT COUNT(*) AS \"total\", " +
            "  NVL(SUM(CASE WHEN complete_time IS NOT NULL THEN 1 ELSE 0 END), 0) AS \"done\", " +
            "  ROUND(AVG(CASE WHEN complete_time IS NOT NULL " +
            "    THEN (CAST(complete_time AS DATE) - CAST(create_time AS DATE)) * 24 END), 1) AS \"avgHours\" " +
            "FROM exam_report " +
            "WHERE (#{date} IS NULL OR TRUNC(create_time) = TRUNC(TO_DATE(#{date}, 'YYYY-MM-DD')))")
    Map<String, Object> reportSummary(@Param("date") String date);

    /** 标本采集情况：按状态分布（COLLECTED 已采 / 其他） */
    @Select("SELECT COUNT(*) AS \"total\", " +
            "  NVL(SUM(CASE WHEN status = 'COLLECTED' THEN 1 ELSE 0 END), 0) AS \"collected\" " +
            "FROM specimen " +
            "WHERE (#{date} IS NULL OR TRUNC(collect_time) = TRUNC(TO_DATE(#{date}, 'YYYY-MM-DD')))")
    Map<String, Object> specimenSummary(@Param("date") String date);
}
