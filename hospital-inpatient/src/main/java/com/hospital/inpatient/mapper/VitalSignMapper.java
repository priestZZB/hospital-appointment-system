package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.VitalSign;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 生命体征 Mapper（注解式） */
@Mapper
public interface VitalSignMapper {

    @Results(id = "vitalMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "temperature", property = "temperature"),
            @Result(column = "pulse", property = "pulse"),
            @Result(column = "respiration", property = "respiration"),
            @Result(column = "blood_pressure", property = "bloodPressure"),
            @Result(column = "blood_oxygen", property = "bloodOxygen"),
            @Result(column = "record_time", property = "recordTime"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT id, admission_id, temperature, pulse, respiration, blood_pressure, blood_oxygen, " +
            "record_time, operator_id, create_time FROM vital_sign " +
            "WHERE admission_id = #{admissionId} ORDER BY record_time DESC LIMIT #{limit}")
    List<VitalSign> selectByAdmission(@Param("admissionId") Long admissionId, @Param("limit") int limit);

    @Insert("INSERT INTO vital_sign (admission_id, temperature, pulse, respiration, blood_pressure, " +
            "blood_oxygen, record_time, operator_id, create_time) " +
            "VALUES (#{admissionId}, #{temperature}, #{pulse}, #{respiration}, #{bloodPressure}, " +
            "#{bloodOxygen}, NOW(), #{operatorId}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(VitalSign vital);
}
