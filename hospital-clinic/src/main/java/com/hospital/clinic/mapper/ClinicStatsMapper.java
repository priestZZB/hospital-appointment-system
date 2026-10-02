package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 门诊统计 Mapper（注解式，迭代12 I1）。口径：挂号=appointment；接诊=checkin 实际报到；收入=挂号费+处方费。 */
@Mapper
public interface ClinicStatsMapper {

    @Select("SELECT COUNT(*) FROM appointment WHERE TRUNC(create_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    long countRegister(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM checkin WHERE TRUNC(checkin_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    long countConsult(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM prescription WHERE TRUNC(create_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    long countPrescription(@Param("date") String date);

    @Select("SELECT NVL(SUM(register_fee), 0) FROM appointment WHERE TRUNC(create_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    BigDecimal sumRegisterFee(@Param("date") String date);

    @Select("SELECT NVL(SUM(total_amount), 0) FROM prescription WHERE TRUNC(create_time) = TO_DATE(#{date}, 'YYYY-MM-DD')")
    BigDecimal sumPrescriptionAmount(@Param("date") String date);

    @Select("SELECT d.dept_name AS \"deptName\", COUNT(*) AS \"cnt\" FROM appointment a " +
            "JOIN department d ON a.department_id = d.id " +
            "WHERE TRUNC(a.create_time) = TO_DATE(#{date}, 'YYYY-MM-DD') " +
            "GROUP BY d.dept_name ORDER BY COUNT(*) DESC FETCH FIRST 5 ROWS ONLY")
    List<Map<String, Object>> topDepartments(@Param("date") String date);

    /** 月内逐日挂号数 */
    @Select("SELECT TO_CHAR(TRUNC(create_time), 'YYYY-MM-DD') AS \"day\", COUNT(*) AS \"cnt\" " +
            "FROM appointment WHERE TO_CHAR(create_time, 'YYYY-MM') = #{month} " +
            "GROUP BY TRUNC(create_time) ORDER BY 1")
    List<Map<String, Object>> registerSeries(@Param("month") String month);

    /** 月内逐日收入（挂号费+处方费） */
    @Select("SELECT \"day\", NVL(SUM(\"amt\"), 0) AS \"income\" FROM (" +
            "  SELECT TO_CHAR(create_time, 'YYYY-MM-DD') AS \"day\", register_fee AS \"amt\" FROM appointment " +
            "   WHERE TO_CHAR(create_time, 'YYYY-MM') = #{month} " +
            "  UNION ALL " +
            "  SELECT TO_CHAR(create_time, 'YYYY-MM-DD'), total_amount FROM prescription " +
            "   WHERE TO_CHAR(create_time, 'YYYY-MM') = #{month}" +
            ") t GROUP BY \"day\" ORDER BY 1")
    List<Map<String, Object>> incomeSeries(@Param("month") String month);

    /** J4 医生工作量：按医生聚合当日挂号/接诊/处方数与处方金额 */
    @Select("SELECT d.id AS \"doctorId\", d.name AS \"doctorName\", dept.dept_name AS \"deptName\", " +
            "  NVL(ap.cnt, 0) AS \"registerCount\", NVL(ck.cnt, 0) AS \"consultCount\", " +
            "  NVL(pr.cnt, 0) AS \"prescriptionCount\", NVL(pr.amt, 0) AS \"prescriptionAmount\" " +
            "FROM doctor d " +
            "JOIN department dept ON dept.id = d.department_id " +
            "LEFT JOIN (SELECT doctor_id, COUNT(*) cnt FROM appointment " +
            "   WHERE TRUNC(create_time) = TRUNC(TO_DATE(#{date}, 'YYYY-MM-DD')) GROUP BY doctor_id) ap ON ap.doctor_id = d.id " +
            "LEFT JOIN (SELECT doctor_id, COUNT(*) cnt FROM checkin " +
            "   WHERE TRUNC(checkin_time) = TRUNC(TO_DATE(#{date}, 'YYYY-MM-DD')) GROUP BY doctor_id) ck ON ck.doctor_id = d.id " +
            "LEFT JOIN (SELECT doctor_id, COUNT(*) cnt, SUM(total_amount) amt FROM prescription " +
            "   WHERE TRUNC(create_time) = TRUNC(TO_DATE(#{date}, 'YYYY-MM-DD')) GROUP BY doctor_id) pr ON pr.doctor_id = d.id " +
            "WHERE d.status = 1 " +
            "ORDER BY NVL(ap.cnt, 0) DESC, NVL(pr.cnt, 0) DESC")
    List<Map<String, Object>> doctorWorkload(@Param("date") String date);
}
