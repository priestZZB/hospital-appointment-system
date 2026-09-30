package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.SurgeryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 手术记录 Mapper（注解式，迭代10 F4；UNIQUE(surgery_id)，一单一条） */
@Mapper
public interface SurgeryRecordMapper {

    String COLS = "id, surgery_id, surgeon_id, incision, procedure_text, findings, specimen_flag, " +
            "blood_loss_ml, duration_min, record_time, create_time";

    @Insert("INSERT INTO surgery_record (surgery_id, surgeon_id, incision, procedure_text, findings, " +
            "specimen_flag, blood_loss_ml, duration_min, record_time, create_time) " +
            "VALUES (#{surgeryId}, #{surgeonId}, #{incision}, #{procedureText}, #{findings}, " +
            "NVL(#{specimenFlag}, 0), #{bloodLossMl}, #{durationMin}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(SurgeryRecord record);

    @Results(id = "recordMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "surgery_id", property = "surgeryId"),
            @Result(column = "surgeon_id", property = "surgeonId"),
            @Result(column = "incision", property = "incision"),
            @Result(column = "procedure_text", property = "procedureText"),
            @Result(column = "findings", property = "findings"),
            @Result(column = "specimen_flag", property = "specimenFlag"),
            @Result(column = "blood_loss_ml", property = "bloodLossMl"),
            @Result(column = "duration_min", property = "durationMin"),
            @Result(column = "record_time", property = "recordTime"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT " + COLS + " FROM surgery_record WHERE surgery_id = #{surgeryId}")
    SurgeryRecord selectBySurgeryId(@Param("surgeryId") Long surgeryId);
}
