package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.InpatientFee;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** 住院费用流水 Mapper（注解式） */
@Mapper
public interface InpatientFeeMapper {

    String COLS = "id, admission_id, fee_type, item_name, amount, bill_date, create_time";

    @Results(id = "feeMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "fee_type", property = "feeType"),
            @Result(column = "item_name", property = "itemName"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "bill_date", property = "billDate"),
            @Result(column = "create_time", property = "createTime")
    })
    @Insert("INSERT INTO inpatient_fee (admission_id, fee_type, item_name, amount, bill_date, create_time) " +
            "VALUES (#{admissionId}, #{feeType}, #{itemName}, #{amount}, #{billDate}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InpatientFee fee);

    @ResultMap("feeMap")
    @Select("SELECT " + COLS + " FROM inpatient_fee WHERE admission_id = #{admissionId} ORDER BY bill_date DESC, id DESC")
    List<InpatientFee> selectByAdmission(@Param("admissionId") Long admissionId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM inpatient_fee WHERE admission_id = #{admissionId}")
    BigDecimal sumByAdmission(@Param("admissionId") Long admissionId);

    /** 当日是否已计过床位费（日结幂等） */
    @Select("SELECT COUNT(*) FROM inpatient_fee WHERE admission_id = #{admissionId} AND fee_type = 'BED' AND bill_date = #{billDate}")
    long countBedFeeOnDate(@Param("admissionId") Long admissionId, @Param("billDate") java.time.LocalDate billDate);
}
