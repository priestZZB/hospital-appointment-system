package com.hospital.inpatient.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 住院统计 Mapper（注解式，迭代12 I1）。口径：出院以病案首页 discharge_time 计；在院 = admission.status='ADMITTED'。 */
@Mapper
public interface InpatientStatsMapper {

    @Select("SELECT COUNT(*) FROM admission WHERE TRUNC(admission_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    long countAdmit(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM medical_record_home WHERE TRUNC(discharge_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    long countDischarge(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM admission WHERE status = 'ADMITTED'")
    long countInWard();

    @Select("SELECT COUNT(*) FROM surgery WHERE TRUNC(scheduled_time) = TO_DATE(#{date}, 'YYYY-MM-DD') " +
            "AND status IN ('IN_OPERATION', 'OPERATED')")
    long countSurgery(@Param("date") String date);

    @Select("SELECT NVL(SUM(amount), 0) FROM deposit WHERE TRUNC(create_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    java.math.BigDecimal sumDeposit(@Param("date") String date);

    @Select("SELECT NVL(SUM(amount), 0) FROM inpatient_fee WHERE bill_date = TO_DATE(#{date}, 'YYYY-MM-DD')")
    java.math.BigDecimal sumIncome(@Param("date") String date);

    /** 月内逐日入院数 */
    @Select("SELECT TO_CHAR(TRUNC(admission_time), 'YYYY-MM-DD') AS \"day\", COUNT(*) AS \"cnt\" " +
            "FROM admission WHERE TO_CHAR(admission_time, 'YYYY-MM') = #{month} " +
            "GROUP BY TRUNC(admission_time) ORDER BY 1")
    List<Map<String, Object>> admitSeries(@Param("month") String month);

    /** 月内逐日收入 */
    @Select("SELECT TO_CHAR(bill_date, 'YYYY-MM-DD') AS \"day\", NVL(SUM(amount), 0) AS \"income\" " +
            "FROM inpatient_fee WHERE TO_CHAR(bill_date, 'YYYY-MM') = #{month} " +
            "GROUP BY bill_date ORDER BY 1")
    List<Map<String, Object>> incomeSeries(@Param("month") String month);
}
