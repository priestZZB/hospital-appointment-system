package com.hospital.payment.mapper;

import com.hospital.payment.entity.SettleRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 收费员日结单表 Mapper（注解式）
 */
@Mapper
public interface SettleRecordMapper {

    /** 插入日结单 */
    @Insert("INSERT INTO settle_record (settle_no, cashier_id, settle_date, total_amount, order_count, detail, create_time) " +
            "VALUES (#{settleNo}, #{cashierId}, #{settleDate}, #{totalAmount}, #{orderCount}, #{detail}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SettleRecord record);

    /** 按收费员 + 日期查询（幂等：同一收费员同一天唯一） */
    @Select("SELECT id, settle_no, cashier_id, settle_date, total_amount, order_count, detail, create_time " +
            "FROM settle_record WHERE cashier_id = #{cashierId} AND settle_date = #{settleDate} LIMIT 1")
    SettleRecord selectByCashierAndDate(@Param("cashierId") Long cashierId,
                                        @Param("settleDate") LocalDate settleDate);
}
