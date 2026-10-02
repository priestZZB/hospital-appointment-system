package com.hospital.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 迭代16 B2 AI 智能层 Mapper（多轮问诊会话 + 通用预测留痕）。
 * 统一返回 Map（列 AS "camelCase"）；CLOB 用 DBMS_LOB.SUBSTR 防止 Jackson 序列化失败。
 */
@Mapper
public interface B2Mapper {

    @Insert("INSERT INTO ai_consult_session (session_no, patient_id, symptom, messages, rounds) " +
            "VALUES (#{sessionNo}, #{patientId}, #{symptom}, #{messages}, #{rounds})")
    int insertSession(@Param("sessionNo") String sessionNo, @Param("patientId") Long patientId,
                      @Param("symptom") String symptom, @Param("messages") String messages,
                      @Param("rounds") int rounds);

    @Update("UPDATE ai_consult_session SET messages = #{messages}, rounds = #{rounds}, " +
            "suggestion = #{suggestion}, status = #{status} WHERE session_no = #{sessionNo}")
    int updateSession(@Param("sessionNo") String sessionNo, @Param("messages") String messages,
                      @Param("rounds") int rounds, @Param("suggestion") String suggestion,
                      @Param("status") String status);

    @Select("SELECT id AS \"id\", session_no AS \"sessionNo\", patient_id AS \"patientId\", " +
            "  symptom AS \"symptom\", DBMS_LOB.SUBSTR(messages, 2000, 1) AS \"messages\", " +
            "  rounds AS \"rounds\", suggestion AS \"suggestion\", status AS \"status\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM ai_consult_session WHERE session_no = #{sessionNo}")
    Map<String, Object> selectSession(@Param("sessionNo") String sessionNo);

    @Select("SELECT id AS \"id\", session_no AS \"sessionNo\", patient_id AS \"patientId\", " +
            "  symptom AS \"symptom\", rounds AS \"rounds\", suggestion AS \"suggestion\", " +
            "  status AS \"status\", TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM ai_consult_session WHERE patient_id = #{patientId} " +
            "ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY")
    List<Map<String, Object>> selectSessionPage(@Param("patientId") Long patientId,
                                                @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM ai_consult_session WHERE patient_id = #{patientId}")
    long countSessionPage(@Param("patientId") Long patientId);

    @Insert("INSERT INTO ai_prediction (pred_no, pred_type, score, detail) " +
            "VALUES (#{predNo}, #{predType}, #{score}, #{detail})")
    int insertPrediction(@Param("predNo") String predNo, @Param("predType") String predType,
                         @Param("score") Double score, @Param("detail") String detail);

    @Select("SELECT id AS \"id\", pred_no AS \"predNo\", pred_type AS \"predType\", score AS \"score\", " +
            "  DBMS_LOB.SUBSTR(detail, 2000, 1) AS \"detail\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM ai_prediction WHERE pred_type = #{predType} " +
            "ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY")
    List<Map<String, Object>> selectPredictionPage(@Param("predType") String predType,
                                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM ai_prediction WHERE pred_type = #{predType}")
    long countPredictionPage(@Param("predType") String predType);
}
