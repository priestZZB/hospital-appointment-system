package com.hospital.ai.mapper;

import com.hospital.ai.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 调用日志表 Mapper
 */
@Mapper
public interface AiCallLogMapper {

    /** 插入日志 */
    int insert(AiCallLog log);

    /** 根据主键查询 */
    AiCallLog selectById(@Param("id") Long id);

    /** 按患者 ID 分页查询 */
    List<AiCallLog> selectByPatientId(@Param("patientId") Long patientId,
                                      @Param("offset") Integer offset,
                                      @Param("limit") Integer limit);

    /** 按患者 ID 统计总数 */
    long countByPatientId(@Param("patientId") Long patientId);
}
