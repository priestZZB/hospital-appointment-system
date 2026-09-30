package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.AnesthesiaRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 麻醉记录 Mapper（注解式，迭代10 F4；UNIQUE(surgery_id)，一单一条，vitals_json 为 CLOB 文本） */
@Mapper
public interface AnesthesiaRecordMapper {

    String COLS = "id, surgery_id, method, asa_grade, induction_time, reversal_time, vitals_json, " +
            "anesthesiologist_id, notes, create_time";

    @Insert("INSERT INTO anesthesia_record (surgery_id, method, asa_grade, induction_time, reversal_time, " +
            "vitals_json, anesthesiologist_id, notes, create_time) " +
            "VALUES (#{surgeryId}, #{method}, #{asaGrade}, #{inductionTime}, #{reversalTime}, " +
            "#{vitalsJson}, #{anesthesiologistId}, #{notes}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AnesthesiaRecord record);

    @Results(id = "anesthesiaMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "surgery_id", property = "surgeryId"),
            @Result(column = "method", property = "method"),
            @Result(column = "asa_grade", property = "asaGrade"),
            @Result(column = "induction_time", property = "inductionTime"),
            @Result(column = "reversal_time", property = "reversalTime"),
            @Result(column = "vitals_json", property = "vitalsJson"),
            @Result(column = "anesthesiologist_id", property = "anesthesiologistId"),
            @Result(column = "notes", property = "notes"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT " + COLS + " FROM anesthesia_record WHERE surgery_id = #{surgeryId}")
    AnesthesiaRecord selectBySurgeryId(@Param("surgeryId") Long surgeryId);
}
