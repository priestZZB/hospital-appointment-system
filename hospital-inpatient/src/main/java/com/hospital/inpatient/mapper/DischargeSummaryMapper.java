package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.DischargeSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 出院小结 Mapper（注解式） */
@Mapper
public interface DischargeSummaryMapper {

    String COLS = "id, admission_id, admission_diag, discharge_diag, treatment_process, discharge_condition, " +
            "discharge_advice, doctor_id, settlement_amount, deposit_balance, discharge_time, create_time, update_time";

    @Insert("INSERT INTO discharge_summary (admission_id, admission_diag, discharge_diag, treatment_process, " +
            "discharge_condition, discharge_advice, doctor_id, settlement_amount, deposit_balance, discharge_time, " +
            "create_time, update_time) VALUES (#{admissionId}, #{admissionDiag}, #{dischargeDiag}, #{treatmentProcess}, " +
            "#{dischargeCondition}, #{dischargeAdvice}, #{doctorId}, #{settlementAmount}, #{depositBalance}, SYSDATE, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DischargeSummary summary);

    @Results(id = "dischargeMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "admission_diag", property = "admissionDiag"),
            @Result(column = "discharge_diag", property = "dischargeDiag"),
            @Result(column = "treatment_process", property = "treatmentProcess"),
            @Result(column = "discharge_condition", property = "dischargeCondition"),
            @Result(column = "discharge_advice", property = "dischargeAdvice"),
            @Result(column = "doctor_id", property = "doctorId"),
            @Result(column = "settlement_amount", property = "settlementAmount"),
            @Result(column = "deposit_balance", property = "depositBalance"),
            @Result(column = "discharge_time", property = "dischargeTime"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM discharge_summary WHERE admission_id = #{admissionId}")
    DischargeSummary selectByAdmission(@Param("admissionId") Long admissionId);

    @Update("UPDATE discharge_summary SET settlement_amount = #{settlement}, deposit_balance = #{balance}, " +
            "update_time = SYSDATE WHERE admission_id = #{admissionId}")
    int updateSettlement(@Param("admissionId") Long admissionId,
                         @Param("settlement") BigDecimal settlement,
                         @Param("balance") BigDecimal balance);
}
