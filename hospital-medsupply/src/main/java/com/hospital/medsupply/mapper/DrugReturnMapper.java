package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugReturn;
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
 * 退药单 Mapper（注解式）
 */
@Mapper
public interface DrugReturnMapper {

    /** 插入退药单 */
    @Insert("INSERT INTO drug_return (return_no, prescription_id, patient_id, drug_id, quantity, " +
            "refund_amount, reason, operator_id, status, create_time) " +
            "VALUES (#{returnNo}, #{prescriptionId}, #{patientId}, #{drugId}, #{quantity}, " +
            "#{refundAmount}, #{reason}, #{operatorId}, #{status}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DrugReturn drugReturn);

    /** 分页查询退药单（支持患者/状态筛选，倒序） */
    @Select("<script>" +
            "SELECT * FROM drug_return WHERE 1=1 " +
            "<if test='patientId != null'> AND patient_id = #{patientId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @Results(id = "returnMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "returnNo", column = "return_no"),
            @Result(property = "prescriptionId", column = "prescription_id"),
            @Result(property = "patientId", column = "patient_id"),
            @Result(property = "drugId", column = "drug_id"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "refundAmount", column = "refund_amount"),
            @Result(property = "reason", column = "reason"),
            @Result(property = "operatorId", column = "operator_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time")
    })
    List<DrugReturn> selectPage(@Param("patientId") Long patientId,
                                @Param("status") String status,
                                @Param("offset") Integer offset,
                                @Param("limit") Integer limit);

    /** 分页统计退药单数量 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM drug_return WHERE 1=1 " +
            "<if test='patientId != null'> AND patient_id = #{patientId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "</script>")
    long countPage(@Param("patientId") Long patientId, @Param("status") String status);
}
