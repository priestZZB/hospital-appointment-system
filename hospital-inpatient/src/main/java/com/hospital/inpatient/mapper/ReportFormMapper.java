package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.ReportForm;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/** 传染病/不良事件上报 Mapper（注解式，迭代12 I2） */
@Mapper
public interface ReportFormMapper {

    String COLS = "id, report_type, patient_id, patient_name, event_name, event_time, occur_department, " +
            "content, reporter_id, reporter_name, status, review_note, create_time";

    @Insert("INSERT INTO report_form (report_type, patient_id, patient_name, event_name, event_time, " +
            "occur_department, content, reporter_id, reporter_name, status, create_time) " +
            "VALUES (#{reportType}, #{patientId}, #{patientName}, #{eventName}, #{eventTime}, " +
            "#{occurDepartment}, #{content}, #{reporterId}, #{reporterName}, 'SUBMITTED', SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ReportForm form);

    @Select("SELECT " + COLS + " FROM report_form WHERE id = #{id}")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "report_type", property = "reportType"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "patient_name", property = "patientName"),
            @Result(column = "event_name", property = "eventName"),
            @Result(column = "event_time", property = "eventTime"),
            @Result(column = "occur_department", property = "occurDepartment"),
            @Result(column = "content", property = "content"),
            @Result(column = "reporter_id", property = "reporterId"),
            @Result(column = "reporter_name", property = "reporterName"),
            @Result(column = "status", property = "status"),
            @Result(column = "review_note", property = "reviewNote"),
            @Result(column = "create_time", property = "createTime")
    })
    ReportForm selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM report_form " +
            "<where>" +
            "  <if test='reportType != null'> AND report_type = #{reportType}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "report_type", property = "reportType"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "patient_name", property = "patientName"),
            @Result(column = "event_name", property = "eventName"),
            @Result(column = "event_time", property = "eventTime"),
            @Result(column = "occur_department", property = "occurDepartment"),
            @Result(column = "content", property = "content"),
            @Result(column = "reporter_id", property = "reporterId"),
            @Result(column = "reporter_name", property = "reporterName"),
            @Result(column = "status", property = "status"),
            @Result(column = "review_note", property = "reviewNote"),
            @Result(column = "create_time", property = "createTime")
    })
    List<ReportForm> selectPage(@Param("reportType") String reportType, @Param("status") String status,
                                @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM report_form " +
            "<where>" +
            "  <if test='reportType != null'> AND report_type = #{reportType}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where></script>")
    long countPage(@Param("reportType") String reportType, @Param("status") String status);

    @Update("UPDATE report_form SET status = 'REVIEWED', review_note = #{note} WHERE id = #{id} AND status = 'SUBMITTED'")
    int review(@Param("id") Long id, @Param("note") String note);
}
