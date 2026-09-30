package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.InformedConsent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 知情同意书 Mapper（注解式，迭代10 F3；UNIQUE(surgery_id, consent_type)，重复签署走 update） */
@Mapper
public interface InformedConsentMapper {

    String COLS = "id, surgery_id, consent_type, patient_sign, signed_time, witness, create_time";

    @Insert("INSERT INTO informed_consent (surgery_id, consent_type, patient_sign, signed_time, witness, create_time) " +
            "VALUES (#{surgeryId}, #{consentType}, #{patientSign}, SYSDATE, #{witness}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InformedConsent consent);

    /** 重复签署更新（signed_time 刷新） */
    @Update("UPDATE informed_consent SET patient_sign = #{patientSign}, signed_time = SYSDATE, " +
            "witness = #{witness} WHERE surgery_id = #{surgeryId} AND consent_type = #{consentType}")
    int updateBySurgeryAndType(InformedConsent consent);

    @Results(id = "consentMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "surgery_id", property = "surgeryId"),
            @Result(column = "consent_type", property = "consentType"),
            @Result(column = "patient_sign", property = "patientSign"),
            @Result(column = "signed_time", property = "signedTime"),
            @Result(column = "witness", property = "witness"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT " + COLS + " FROM informed_consent " +
            "WHERE surgery_id = #{surgeryId} AND consent_type = #{consentType}")
    InformedConsent selectBySurgeryAndType(@Param("surgeryId") Long surgeryId, @Param("consentType") String consentType);

    @ResultMap("consentMap")
    @Select("SELECT " + COLS + " FROM informed_consent WHERE surgery_id = #{surgeryId} ORDER BY id")
    List<InformedConsent> selectBySurgeryId(@Param("surgeryId") Long surgeryId);
}
