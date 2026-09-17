package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.Deposit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** 预交金 Mapper（注解式） */
@Mapper
public interface DepositMapper {

    @Results(id = "depositMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "pay_method", property = "payMethod"),
            @Result(column = "balance_after", property = "balanceAfter"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT id, admission_id, amount, pay_method, balance_after, operator_id, create_time " +
            "FROM deposit WHERE admission_id = #{admissionId} ORDER BY create_time DESC")
    List<Deposit> selectByAdmission(@Param("admissionId") Long admissionId);

    @Insert("INSERT INTO deposit (admission_id, amount, pay_method, balance_after, operator_id, create_time) " +
            "VALUES (#{admissionId}, #{amount}, #{payMethod}, #{balanceAfter}, #{operatorId}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Deposit deposit);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM deposit WHERE admission_id = #{admissionId}")
    BigDecimal sumByAdmission(@Param("admissionId") Long admissionId);
}
