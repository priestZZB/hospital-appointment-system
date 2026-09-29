package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.InfusionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 输液执行记录表 Mapper（注解式）
 */
@Mapper
public interface InfusionRecordMapper {

    /** 插入执行记录 */
    @Insert("INSERT INTO infusion_record (infusion_order_id, record_type, record_content, " +
            "skin_test_result, drop_rate, operator_id, operator_name, create_time) " +
            "VALUES (#{infusionOrderId}, #{recordType}, #{recordContent}, " +
            "#{skinTestResult}, #{dropRate}, #{operatorId}, #{operatorName}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InfusionRecord record);

    /** 按输液单 ID 查询执行记录（按时间倒序） */
    @Select("SELECT * FROM infusion_record WHERE infusion_order_id = #{infusionOrderId} " +
            "ORDER BY create_time DESC")
    List<InfusionRecord> selectByOrderId(@Param("infusionOrderId") Long infusionOrderId);
}
