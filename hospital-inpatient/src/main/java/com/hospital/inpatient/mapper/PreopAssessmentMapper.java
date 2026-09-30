package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.PreopAssessment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 术前评估 Mapper（注解式，迭代10 F3；UNIQUE(surgery_id)，重复提交走 update） */
@Mapper
public interface PreopAssessmentMapper {

    String COLS = "id, surgery_id, asa_grade, risk_factors, assessment_text, conclusion, assessor_id, assessment_time, create_time";

    @Insert("INSERT INTO preop_assessment (surgery_id, asa_grade, risk_factors, assessment_text, " +
            "conclusion, assessor_id, assessment_time, create_time) " +
            "VALUES (#{surgeryId}, #{asaGrade}, #{riskFactors}, #{assessmentText}, " +
            "#{conclusion}, #{assessorId}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PreopAssessment assessment);

    /** 重复提交更新（assessment_time 刷新） */
    @Update("UPDATE preop_assessment SET asa_grade = #{asaGrade}, risk_factors = #{riskFactors}, " +
            "assessment_text = #{assessmentText}, conclusion = #{conclusion}, assessor_id = #{assessorId}, " +
            "assessment_time = SYSDATE WHERE surgery_id = #{surgeryId}")
    int updateBySurgeryId(PreopAssessment assessment);

    @Results(id = "preopMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "surgery_id", property = "surgeryId"),
            @Result(column = "asa_grade", property = "asaGrade"),
            @Result(column = "risk_factors", property = "riskFactors"),
            @Result(column = "assessment_text", property = "assessmentText"),
            @Result(column = "conclusion", property = "conclusion"),
            @Result(column = "assessor_id", property = "assessorId"),
            @Result(column = "assessment_time", property = "assessmentTime"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT " + COLS + " FROM preop_assessment WHERE surgery_id = #{surgeryId}")
    PreopAssessment selectBySurgeryId(@Param("surgeryId") Long surgeryId);
}
