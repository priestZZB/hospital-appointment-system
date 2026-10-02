package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 就诊满意度评价 Mapper（迭代13 K3，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface EvaluationMapper {

    @Insert("INSERT INTO evaluation (appointment_id, patient_id, doctor_id, department_id, score, content) " +
            "VALUES (#{appointmentId}, #{patientId}, #{doctorId}, #{departmentId}, #{score}, #{content})")
    int insert(@Param("appointmentId") Long appointmentId, @Param("patientId") Long patientId,
               @Param("doctorId") Long doctorId, @Param("departmentId") Long departmentId,
               @Param("score") int score, @Param("content") String content);

    @Select("SELECT COUNT(*) FROM evaluation WHERE appointment_id = #{appointmentId}")
    long existsByAppointment(@Param("appointmentId") Long appointmentId);

    /** 某医生的评价列表（取 20 条）+ 均分 */
    @Select("SELECT e.id AS \"id\", e.score AS \"score\", e.content AS \"content\", " +
            "  TO_CHAR(e.create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\", d.name AS \"doctorName\" " +
            "FROM evaluation e JOIN doctor d ON d.id = e.doctor_id " +
            "WHERE e.doctor_id = #{doctorId} ORDER BY e.create_time DESC FETCH FIRST 20 ROWS ONLY")
    List<Map<String, Object>> selectByDoctor(@Param("doctorId") Long doctorId);

    @Select("SELECT COUNT(*) AS \"count\", ROUND(AVG(score), 1) AS \"avgScore\" FROM evaluation WHERE doctor_id = #{doctorId}")
    Map<String, Object> doctorSummary(@Param("doctorId") Long doctorId);

    /** 管理端分页 */
    @Select("<script>SELECT e.id AS \"id\", e.appointment_id AS \"appointmentId\", e.patient_id AS \"patientId\", " +
            "  e.doctor_id AS \"doctorId\", d.name AS \"doctorName\", dept.dept_name AS \"deptName\", " +
            "  e.score AS \"score\", e.content AS \"content\", " +
            "  TO_CHAR(e.create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM evaluation e JOIN doctor d ON d.id = e.doctor_id " +
            "LEFT JOIN department dept ON dept.id = e.department_id " +
            "<where>" +
            "  <if test='doctorId != null'> AND e.doctor_id = #{doctorId}</if>" +
            "  <if test='minScore != null'> AND e.score &gt;= #{minScore}</if>" +
            "</where> ORDER BY e.create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("doctorId") Long doctorId, @Param("minScore") Integer minScore,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM evaluation e " +
            "<where>" +
            "  <if test='doctorId != null'> AND e.doctor_id = #{doctorId}</if>" +
            "  <if test='minScore != null'> AND e.score &gt;= #{minScore}</if>" +
            "</where></script>")
    long countPage(@Param("doctorId") Long doctorId, @Param("minScore") Integer minScore);
}
