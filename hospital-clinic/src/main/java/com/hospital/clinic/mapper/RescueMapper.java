package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 抢救记录 Mapper（迭代14 G2，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface RescueMapper {

    @Insert("INSERT INTO rescue_record (rescue_no, patient_id, patient_name, triage_id, measures, participants) " +
            "VALUES (#{rescueNo}, #{patientId}, #{patientName}, #{triageId}, #{measures}, #{participants})")
    int insert(@Param("rescueNo") String rescueNo, @Param("patientId") Long patientId,
               @Param("patientName") String patientName, @Param("triageId") Long triageId,
               @Param("measures") String measures, @Param("participants") String participants);

    @Select("SELECT id FROM rescue_record WHERE rescue_no = #{rescueNo}")
    Long selectIdByNo(@Param("rescueNo") String rescueNo);

    @Select("<script>SELECT r.id AS \"id\", r.rescue_no AS \"rescueNo\", r.patient_id AS \"patientId\", " +
            "  r.patient_name AS \"patientName\", r.triage_id AS \"triageId\", " +
            "  DBMS_LOB.SUBSTR(r.measures, 2000, 1) AS \"measures\", " +
            "  r.participants AS \"participants\", r.outcome AS \"outcome\", " +
            "  TO_CHAR(r.start_time, 'YYYY-MM-DD HH24:MI') AS \"startTime\", " +
            "  TO_CHAR(r.end_time, 'YYYY-MM-DD HH24:MI') AS \"endTime\" " +
            "FROM rescue_record r " +
            "<where>" +
            "  <if test='outcome != null'> AND r.outcome = #{outcome}</if>" +
            "  <if test='keyword != null'> AND r.patient_name LIKE '%'||#{keyword}||'%'</if>" +
            "</where> ORDER BY r.start_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("outcome") String outcome, @Param("keyword") String keyword,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM rescue_record r " +
            "<where>" +
            "  <if test='outcome != null'> AND r.outcome = #{outcome}</if>" +
            "  <if test='keyword != null'> AND r.patient_name LIKE '%'||#{keyword}||'%'</if>" +
            "</where></script>")
    long countPage(@Param("outcome") String outcome, @Param("keyword") String keyword);

    /** 结束抢救并落结局 */
    @Update("UPDATE rescue_record SET outcome = #{outcome}, end_time = SYSDATE " +
            "WHERE id = #{id} AND outcome = 'ONGOING'")
    int finish(@Param("id") Long id, @Param("outcome") String outcome);
}
