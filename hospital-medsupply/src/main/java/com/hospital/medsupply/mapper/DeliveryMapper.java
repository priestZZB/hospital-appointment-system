package com.hospital.medsupply.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 线上购药配送 Mapper（迭代13 K2，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface DeliveryMapper {

    String COLS = "id AS \"id\", delivery_no AS \"deliveryNo\", prescription_id AS \"prescriptionId\", "
            + "patient_id AS \"patientId\", patient_name AS \"patientName\", drug_summary AS \"drugSummary\", "
            + "receiver_name AS \"receiverName\", receiver_phone AS \"receiverPhone\", address AS \"address\", "
            + "status AS \"status\", "
            + "TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\", "
            + "TO_CHAR(dispatch_time, 'YYYY-MM-DD HH24:MI') AS \"dispatchTime\", "
            + "TO_CHAR(deliver_time, 'YYYY-MM-DD HH24:MI') AS \"deliverTime\"";

    @Insert("INSERT INTO delivery (delivery_no, prescription_id, patient_id, patient_name, drug_summary, " +
            "receiver_name, receiver_phone, address, operator_id) " +
            "VALUES (#{deliveryNo}, #{prescriptionId}, #{patientId}, #{patientName}, #{drugSummary}, " +
            "#{receiverName}, #{receiverPhone}, #{address}, #{operatorId})")
    int insert(@Param("deliveryNo") String deliveryNo, @Param("prescriptionId") Long prescriptionId,
               @Param("patientId") Long patientId, @Param("patientName") String patientName,
               @Param("drugSummary") String drugSummary, @Param("receiverName") String receiverName,
               @Param("receiverPhone") String receiverPhone, @Param("address") String address,
               @Param("operatorId") Long operatorId);

    @Select("SELECT id FROM delivery WHERE delivery_no = #{deliveryNo}")
    Long selectIdByNo(@Param("deliveryNo") String deliveryNo);

    @Select("SELECT " + COLS + " FROM delivery WHERE id = #{id}")
    Map<String, Object> selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM delivery " +
            "<where>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "  <if test='patientId != null'> AND patient_id = #{patientId}</if>" +
            "</where> ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("status") String status, @Param("patientId") Long patientId,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM delivery " +
            "<where>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "  <if test='patientId != null'> AND patient_id = #{patientId}</if>" +
            "</where></script>")
    long countPage(@Param("status") String status, @Param("patientId") Long patientId);

    @Update("UPDATE delivery SET status = 'DISPATCHED', dispatch_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'CREATED'")
    int dispatch(@Param("id") Long id);

    @Update("UPDATE delivery SET status = 'DELIVERED', deliver_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'DISPATCHED'")
    int deliver(@Param("id") Long id);
}
