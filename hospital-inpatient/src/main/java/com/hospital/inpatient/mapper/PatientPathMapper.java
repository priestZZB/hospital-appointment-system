package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.PatientPath;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 患者路径 Mapper（注解式，迭代12 J1） */
@Mapper
public interface PatientPathMapper {

    String COLS = "id, template_id, admission_id, patient_id, enter_time, current_day, status, variation_reason, exit_time";

    @Insert("INSERT INTO patient_path (template_id, admission_id, patient_id, enter_time, current_day, status) " +
            "VALUES (#{templateId}, #{admissionId}, #{patientId}, SYSDATE, 1, 'IN_PATH')")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PatientPath path);

    @Select("SELECT " + COLS + " FROM patient_path WHERE id = #{id}")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "template_id", property = "templateId"),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "enter_time", property = "enterTime"),
            @Result(column = "current_day", property = "currentDay"),
            @Result(column = "status", property = "status"),
            @Result(column = "variation_reason", property = "variationReason"),
            @Result(column = "exit_time", property = "exitTime")
    })
    PatientPath selectById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM patient_path WHERE admission_id = #{admissionId} AND status = 'IN_PATH'")
    int countInPath(@Param("admissionId") Long admissionId);

    @Select("<script>SELECT " + COLS + " FROM patient_path " +
            "<where>" +
            "  <if test='admissionId != null'> AND admission_id = #{admissionId}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "template_id", property = "templateId"),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "enter_time", property = "enterTime"),
            @Result(column = "current_day", property = "currentDay"),
            @Result(column = "status", property = "status"),
            @Result(column = "variation_reason", property = "variationReason"),
            @Result(column = "exit_time", property = "exitTime")
    })
    List<PatientPath> selectPage(@Param("admissionId") Long admissionId, @Param("status") String status,
                                 @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM patient_path " +
            "<where>" +
            "  <if test='admissionId != null'> AND admission_id = #{admissionId}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where></script>")
    long countPage(@Param("admissionId") Long admissionId, @Param("status") String status);

    @Update("UPDATE patient_path SET current_day = current_day + 1, " +
            "status = CASE WHEN current_day + 1 >= (SELECT standard_days FROM path_template WHERE id = template_id) " +
            "THEN 'COMPLETED' ELSE status END, " +
            "exit_time = CASE WHEN current_day + 1 >= (SELECT standard_days FROM path_template WHERE id = template_id) " +
            "THEN SYSDATE ELSE exit_time END " +
            "WHERE id = #{id} AND status = 'IN_PATH'")
    int advance(@Param("id") Long id);

    @Update("UPDATE patient_path SET status = 'VARIATION', variation_reason = #{reason} WHERE id = #{id} AND status = 'IN_PATH'")
    int vary(@Param("id") Long id, @Param("reason") String reason);

    @Update("UPDATE patient_path SET status = 'EXITED', variation_reason = #{reason}, exit_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('IN_PATH', 'VARIATION')")
    int exit(@Param("id") Long id, @Param("reason") String reason);

    @Update("UPDATE patient_path SET status = 'COMPLETED', exit_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('IN_PATH', 'VARIATION')")
    int complete(@Param("id") Long id);
}
