package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.MedicalRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 病案 Mapper（注解式，迭代12 I3） */
@Mapper
public interface MedicalRecordMapper {

    String COLS = "id, record_no, patient_id, patient_name, admission_id, diagnosis, archive_status, archive_time, create_time";

    @Insert("INSERT INTO medical_record (record_no, patient_id, patient_name, admission_id, diagnosis, archive_status, create_time) " +
            "VALUES ('MR'||TO_CHAR(SYSDATE,'YYYYMMDD')||LPAD(seq_record_no.NEXTVAL,6,'0'), " +
            "#{patientId}, #{patientName}, #{admissionId}, #{diagnosis}, 'IN_WARD', SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MedicalRecord record);

    @Select("SELECT " + COLS + " FROM medical_record WHERE id = #{id}")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "record_no", property = "recordNo"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "patient_name", property = "patientName"),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "diagnosis", property = "diagnosis"),
            @Result(column = "archive_status", property = "archiveStatus"),
            @Result(column = "archive_time", property = "archiveTime"),
            @Result(column = "create_time", property = "createTime")
    })
    MedicalRecord selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM medical_record " +
            "<where>" +
            "  <if test='archiveStatus != null'> AND archive_status = #{archiveStatus}</if>" +
            "  <if test='keyword != null'> AND (record_no LIKE '%'||UPPER(#{keyword})||'%' OR patient_name LIKE '%'||#{keyword}||'%')</if>" +
            "</where>" +
            " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "record_no", property = "recordNo"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "patient_name", property = "patientName"),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "diagnosis", property = "diagnosis"),
            @Result(column = "archive_status", property = "archiveStatus"),
            @Result(column = "archive_time", property = "archiveTime"),
            @Result(column = "create_time", property = "createTime")
    })
    List<MedicalRecord> selectPage(@Param("archiveStatus") String archiveStatus, @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM medical_record " +
            "<where>" +
            "  <if test='archiveStatus != null'> AND archive_status = #{archiveStatus}</if>" +
            "  <if test='keyword != null'> AND (record_no LIKE '%'||UPPER(#{keyword})||'%' OR patient_name LIKE '%'||#{keyword}||'%')</if>" +
            "</where></script>")
    long countPage(@Param("archiveStatus") String archiveStatus, @Param("keyword") String keyword);

    @Update("UPDATE medical_record SET archive_status = 'ARCHIVED', archive_time = SYSDATE " +
            "WHERE id = #{id} AND archive_status = 'IN_WARD'")
    int archive(@Param("id") Long id);
}
