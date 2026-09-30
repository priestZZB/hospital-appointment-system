package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DecoctionOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 中药代煎订单 Mapper（注解式）
 */
@Mapper
public interface DecoctionOrderMapper {

    /** 插入代煎订单 */
    @Insert("INSERT INTO decoction_order (order_no, prescription_id, patient_id, doses, decoction_type, " +
            "status, pickup_code, fee_amount, remark, create_time, update_time) " +
            "VALUES (#{orderNo}, #{prescriptionId}, #{patientId}, #{doses}, #{decoctionType}, " +
            "#{status}, #{pickupCode}, #{feeAmount}, #{remark}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DecoctionOrder order);

    /** 状态流转（乐观条件：仅期望状态可流转） */
    @Update("UPDATE decoction_order SET status = #{status}, update_time = SYSDATE " +
            "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus);

    /** 根据主键查询 */
    @Select("SELECT * FROM decoction_order WHERE id = #{id}")
    @Results(id = "decoctionMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "orderNo", column = "order_no"),
            @Result(property = "prescriptionId", column = "prescription_id"),
            @Result(property = "patientId", column = "patient_id"),
            @Result(property = "doses", column = "doses"),
            @Result(property = "decoctionType", column = "decoction_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "pickupCode", column = "pickup_code"),
            @Result(property = "feeAmount", column = "fee_amount"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    DecoctionOrder selectById(@Param("id") Long id);

    /** 按状态分页查询（药师工作台，状态可选，倒序） */
    @Select("<script>" +
            "SELECT * FROM decoction_order WHERE 1=1 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @ResultMap("decoctionMap")
    List<DecoctionOrder> selectByPage(@Param("status") String status,
                                      @Param("offset") Integer offset,
                                      @Param("limit") Integer limit);

    /** 按状态分页统计 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM decoction_order WHERE 1=1 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "</script>")
    long countByPage(@Param("status") String status);

    /** 按患者查询（本人订单，倒序） */
    @Select("SELECT * FROM decoction_order WHERE patient_id = #{patientId} " +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY")
    @ResultMap("decoctionMap")
    List<DecoctionOrder> selectByPatient(@Param("patientId") Long patientId,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);

    /** 按患者统计总数 */
    @Select("SELECT COUNT(*) FROM decoction_order WHERE patient_id = #{patientId}")
    long countByPatient(@Param("patientId") Long patientId);
}
