package com.hospital.payment.mapper;

import com.hospital.payment.entity.ChargeItemAdjustLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收费项目调价历史 Mapper（注解式，迭代11 H4）
 */
@Mapper
public interface ChargeItemAdjustLogMapper {

    @Insert("INSERT INTO charge_item_adjust_log (item_id, old_price, new_price, reason, operator_id, create_time) " +
            "VALUES (#{itemId}, #{oldPrice}, #{newPrice}, #{reason}, #{operatorId}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChargeItemAdjustLog log);

    @Select("SELECT id, item_id, old_price, new_price, reason, operator_id, create_time " +
            "FROM charge_item_adjust_log WHERE item_id = #{itemId} ORDER BY create_time DESC, id DESC")
    List<ChargeItemAdjustLog> selectByItemId(@Param("itemId") Long itemId);
}
