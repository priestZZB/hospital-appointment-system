package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 遗传排班建议 Mapper（迭代15 B1，注解式）。
 * 统一返回 Map（列 AS "camelCase"）。
 */
@Mapper
public interface ScheduleSuggestionMapper {

    @Insert("INSERT INTO schedule_suggestion (batch_no, week_start, department_id, doctor_id, doctor_name, " +
            "plan_days, fitness) VALUES (#{batchNo}, TO_DATE(#{weekStart}, 'YYYY-MM-DD'), #{departmentId}, " +
            "#{doctorId}, #{doctorName}, #{planDays}, #{fitness})")
    int insert(@Param("batchNo") String batchNo, @Param("weekStart") String weekStart,
               @Param("departmentId") Long departmentId, @Param("doctorId") Long doctorId,
               @Param("doctorName") String doctorName, @Param("planDays") String planDays,
               @Param("fitness") double fitness);

    @Select("SELECT id AS \"id\", batch_no AS \"batchNo\", TO_CHAR(week_start, 'YYYY-MM-DD') AS \"weekStart\", " +
            "  department_id AS \"departmentId\", doctor_id AS \"doctorId\", doctor_name AS \"doctorName\", " +
            "  plan_days AS \"planDays\", fitness AS \"fitness\", status AS \"status\" " +
            "FROM schedule_suggestion WHERE batch_no = #{batchNo} ORDER BY doctor_id")
    List<Map<String, Object>> selectByBatch(@Param("batchNo") String batchNo);

    @Select("<script>SELECT id AS \"id\", batch_no AS \"batchNo\", TO_CHAR(week_start, 'YYYY-MM-DD') AS \"weekStart\", " +
            "  department_id AS \"departmentId\", doctor_id AS \"doctorId\", doctor_name AS \"doctorName\", " +
            "  plan_days AS \"planDays\", fitness AS \"fitness\", status AS \"status\" " +
            "FROM schedule_suggestion " +
            "<where>" +
            "  <if test='departmentId != null'> AND department_id = #{departmentId}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where> ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("departmentId") Long departmentId, @Param("status") String status,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Update("UPDATE schedule_suggestion SET status = 'APPLIED' WHERE batch_no = #{batchNo} AND status = 'DRAFT'")
    long markApplied(@Param("batchNo") String batchNo);

    /** 科室在职医生 */
    @Select("<script>SELECT id AS \"id\", name AS \"name\" FROM doctor " +
            "<where> status = 1" +
            "  <if test='departmentId != null'> AND department_id = #{departmentId}</if>" +
            "</where> ORDER BY id</script>")
    List<Map<String, Object>> selectDoctors(@Param("departmentId") Long departmentId);

    /** 某周已有排班的医生日（用于 GA 保留约束；排除已停诊排班，避免建议复活停诊班） */
    @Select("SELECT doctor_id AS \"doctorId\", TO_CHAR(schedule_date, 'YYYY-MM-DD') AS \"d\" " +
            "FROM schedule WHERE status = 1 AND schedule_date BETWEEN TO_DATE(#{weekStart}, 'YYYY-MM-DD') " +
            "AND TO_DATE(#{weekStart}, 'YYYY-MM-DD') + 6")
    List<Map<String, Object>> selectExistingWeek(@Param("weekStart") String weekStart);
}
