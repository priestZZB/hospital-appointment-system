package com.hospital.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 爽约预测 Mapper（迭代15 B1-4，注解式）。
 * 统一返回 Map（列 AS "camelCase"）。
 */
@Mapper
public interface NoShowMapper {

    @Insert("INSERT INTO no_show_prediction (patient_id, appointment_id, score, risk_level, factors) " +
            "VALUES (#{patientId}, #{appointmentId}, #{score}, #{riskLevel}, #{factors})")
    int insertPrediction(@Param("patientId") Long patientId, @Param("appointmentId") Long appointmentId,
                         @Param("score") double score, @Param("riskLevel") String riskLevel,
                         @Param("factors") String factors);

    @Select("SELECT id AS \"id\", patient_id AS \"patientId\", appointment_id AS \"appointmentId\", " +
            "  score AS \"score\", risk_level AS \"riskLevel\", factors AS \"factors\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM no_show_prediction WHERE patient_id = #{patientId} " +
            "ORDER BY create_time DESC FETCH FIRST 20 ROWS ONLY")
    List<Map<String, Object>> selectByPatient(@Param("patientId") Long patientId);

    @Select("SELECT id AS \"id\", patient_id AS \"patientId\", appointment_id AS \"appointmentId\", " +
            "  score AS \"score\", risk_level AS \"riskLevel\", factors AS \"factors\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM no_show_prediction ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY")
    List<Map<String, Object>> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM no_show_prediction")
    long countPage();

    /** 患者历史预测均分（模型自评估参考） */
    @Select("SELECT COUNT(*) AS \"cnt\", ROUND(AVG(score), 4) AS \"avgScore\" FROM no_show_prediction WHERE patient_id = #{patientId}")
    Map<String, Object> patientSummary(@Param("patientId") Long patientId);
}
