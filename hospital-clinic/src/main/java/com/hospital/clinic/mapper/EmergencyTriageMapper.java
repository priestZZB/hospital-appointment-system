package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 急诊预检分级 Mapper（迭代14 G1，注解式）。
 * 与迭代 9 的分诊台（TriageController/TriageService，就诊叫号优先级）区分：
 * 本 Mapper 面向急诊四级预检（RED/ORANGE/YELLOW/GREEN）登记与流转。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface EmergencyTriageMapper {

    String COLS = "t.id AS \"id\", t.visit_no AS \"visitNo\", t.patient_id AS \"patientId\", "
            + "t.patient_name AS \"patientName\", t.triage_level AS \"triageLevel\", "
            + "t.chief_complaint AS \"chiefComplaint\", t.temperature AS \"temperature\", "
            + "t.pulse AS \"pulse\", t.blood_pressure AS \"bloodPressure\", "
            + "t.department_id AS \"departmentId\", dept.dept_name AS \"deptName\", "
            + "t.doctor_id AS \"doctorId\", d.name AS \"doctorName\", t.status AS \"status\", "
            + "TO_CHAR(t.create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\"";

    @Insert("INSERT INTO triage_record (visit_no, patient_id, patient_name, triage_level, chief_complaint, " +
            "temperature, pulse, blood_pressure, department_id, doctor_id, triage_by) " +
            "VALUES (#{visitNo}, #{patientId}, #{patientName}, #{triageLevel}, #{chiefComplaint}, " +
            "#{temperature}, #{pulse}, #{bloodPressure}, #{departmentId}, #{doctorId}, #{triageBy})")
    int insert(@Param("visitNo") String visitNo, @Param("patientId") Long patientId,
               @Param("patientName") String patientName, @Param("triageLevel") String triageLevel,
               @Param("chiefComplaint") String chiefComplaint, @Param("temperature") Double temperature,
               @Param("pulse") Integer pulse, @Param("bloodPressure") String bloodPressure,
               @Param("departmentId") Long departmentId, @Param("doctorId") Long doctorId,
               @Param("triageBy") Long triageBy);

    @Select("SELECT id FROM triage_record WHERE visit_no = #{visitNo}")
    Long selectIdByVisitNo(@Param("visitNo") String visitNo);

    @Select("<script>SELECT " + COLS + " FROM triage_record t " +
            "LEFT JOIN department dept ON dept.id = t.department_id " +
            "LEFT JOIN doctor d ON d.id = t.doctor_id " +
            "<where>" +
            "  <if test='triageLevel != null'> AND t.triage_level = #{triageLevel}</if>" +
            "  <if test='status != null'> AND t.status = #{status}</if>" +
            "  <if test='keyword != null'> AND (t.patient_name LIKE '%'||#{keyword}||'%' OR t.visit_no LIKE '%'||#{keyword}||'%')</if>" +
            "</where> ORDER BY t.create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("triageLevel") String triageLevel, @Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM triage_record t " +
            "<where>" +
            "  <if test='triageLevel != null'> AND t.triage_level = #{triageLevel}</if>" +
            "  <if test='status != null'> AND t.status = #{status}</if>" +
            "  <if test='keyword != null'> AND (t.patient_name LIKE '%'||#{keyword}||'%' OR t.visit_no LIKE '%'||#{keyword}||'%')</if>" +
            "</where></script>")
    long countPage(@Param("triageLevel") String triageLevel, @Param("status") String status,
                   @Param("keyword") String keyword);

    /** 分级统计（今日） */
    @Select("SELECT triage_level AS \"level\", COUNT(*) AS \"cnt\" FROM triage_record " +
            "WHERE TRUNC(create_time) = TRUNC(SYSDATE) GROUP BY triage_level")
    List<Map<String, Object>> todayLevelStats();

    /** 更新就诊状态（WAITING→TREATING→DONE） */
    @Update("UPDATE triage_record SET status = #{status}, update_time = SYSDATE " +
            "WHERE id = #{id} AND status = #{fromStatus}")
    int updateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                     @Param("status") String status);
}
