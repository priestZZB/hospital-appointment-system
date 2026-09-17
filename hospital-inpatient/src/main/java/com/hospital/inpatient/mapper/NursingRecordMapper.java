package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.NursingRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 护理病历 Mapper（注解式） */
@Mapper
public interface NursingRecordMapper {

    String COLS = "id, admission_id, record_type, content, intake_ml, output_ml, nurse_id, record_time, create_time";

    @Results(id = "nursingMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "record_type", property = "recordType"),
            @Result(column = "content", property = "content"),
            @Result(column = "intake_ml", property = "intakeMl"),
            @Result(column = "output_ml", property = "outputMl"),
            @Result(column = "nurse_id", property = "nurseId"),
            @Result(column = "record_time", property = "recordTime"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("<script>SELECT " + COLS + " FROM nursing_record WHERE admission_id = #{admissionId} " +
            "<if test='recordType != null and recordType != &quot;&quot;'> AND record_type = #{recordType} </if>" +
            "ORDER BY record_time DESC LIMIT #{limit}</script>")
    List<NursingRecord> selectByAdmission(@Param("admissionId") Long admissionId,
                                          @Param("recordType") String recordType,
                                          @Param("limit") int limit);

    @Insert("INSERT INTO nursing_record (admission_id, record_type, content, intake_ml, output_ml, " +
            "nurse_id, record_time, create_time) VALUES (#{admissionId}, #{recordType}, #{content}, " +
            "#{intakeMl}, #{outputMl}, #{nurseId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NursingRecord record);
}
