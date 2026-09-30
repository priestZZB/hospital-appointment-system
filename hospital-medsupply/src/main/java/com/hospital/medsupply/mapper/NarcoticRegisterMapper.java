package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.NarcoticRegister;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 麻精药品五专登记 Mapper（注解式）
 */
@Mapper
public interface NarcoticRegisterMapper {

    /** 插入登记流水 */
    @Insert("INSERT INTO narcotic_register (drug_id, prescription_id, patient_id, action, quantity, " +
            "balance, operator_id, remark, create_time) " +
            "VALUES (#{drugId}, #{prescriptionId}, #{patientId}, #{action}, #{quantity}, " +
            "#{balance}, #{operatorId}, #{remark}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(NarcoticRegister register);

    /** 按药品查询登记流水（最新在前，五专登记册） */
    @Select("SELECT * FROM narcotic_register WHERE drug_id = #{drugId} ORDER BY create_time DESC, id DESC")
    @Results(id = "narcoticMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "drugId", column = "drug_id"),
            @Result(property = "prescriptionId", column = "prescription_id"),
            @Result(property = "patientId", column = "patient_id"),
            @Result(property = "action", column = "action"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "balance", column = "balance"),
            @Result(property = "operatorId", column = "operator_id"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "createTime", column = "create_time")
    })
    List<NarcoticRegister> selectByDrugId(@Param("drugId") Long drugId);

    /** 查询药品最新一条登记流水（结存连续性计算用） */
    @Select("SELECT * FROM narcotic_register WHERE drug_id = #{drugId} " +
            "ORDER BY create_time DESC, id DESC FETCH FIRST 1 ROWS ONLY")
    @ResultMap("narcoticMap")
    NarcoticRegister selectLatestByDrugId(@Param("drugId") Long drugId);
}
