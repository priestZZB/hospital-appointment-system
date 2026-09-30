package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.ScheduleGenerateLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 自动排班 Job 执行日志 Mapper（迭代9 A8，注解式）
 */
@Mapper
public interface ScheduleGenerateLogMapper {

    /** 写入一条执行日志 */
    @Insert("INSERT INTO schedule_generate_log (biz_date, created_count, message, create_time) "
            + "VALUES (#{bizDate}, COALESCE(#{createdCount}, 0), #{message}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ScheduleGenerateLog logEntity);

    /** 查询某目标日期的最近执行日志（倒序，供排查/展示） */
    @Select("SELECT id, biz_date, created_count, message, create_time "
            + "FROM schedule_generate_log WHERE biz_date = #{bizDate} "
            + "ORDER BY id DESC FETCH FIRST 10 ROWS ONLY")
    List<ScheduleGenerateLog> selectByBizDate(@Param("bizDate") LocalDate bizDate);
}
