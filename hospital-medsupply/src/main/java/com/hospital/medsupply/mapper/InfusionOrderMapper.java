package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.InfusionOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 输液单表 Mapper（注解式）
 */
@Mapper
public interface InfusionOrderMapper {

    /** 插入输液单 */
    @Insert("INSERT INTO infusion_order (infusion_no, medical_record_id, patient_id, doctor_id, " +
            "drug_id, drug_name, dosage, usage_method, frequency, days, skin_test_required, " +
            "unit_price, total_amount, pay_status, status, create_time, update_time) " +
            "VALUES (#{infusionNo}, #{medicalRecordId}, #{patientId}, #{doctorId}, " +
            "#{drugId}, #{drugName}, #{dosage}, #{usageMethod}, #{frequency}, #{days}, #{skinTestRequired}, " +
            "#{unitPrice}, #{totalAmount}, #{payStatus}, #{status}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InfusionOrder order);

    /** 根据主键查询 */
    @Select("SELECT * FROM infusion_order WHERE id = #{id}")
    InfusionOrder selectById(@Param("id") Long id);

    /** 按患者 ID 分页查询（倒序） */
    @Select("SELECT * FROM infusion_order WHERE patient_id = #{patientId} " +
            "ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY")
    List<InfusionOrder> selectByPatientId(@Param("patientId") Long patientId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /** 按状态分页查询（已缴费优先，护士站待执行列表） */
    @Select("SELECT * FROM infusion_order WHERE status = #{status} " +
            "ORDER BY CASE WHEN pay_status = 'PAID' THEN 1 ELSE 0 END DESC, create_time ASC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY")
    List<InfusionOrder> selectByStatus(@Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 按患者 ID 统计总数 */
    @Select("SELECT COUNT(*) FROM infusion_order WHERE patient_id = #{patientId}")
    long countByPatientId(@Param("patientId") Long patientId);

    /** 按状态统计总数 */
    @Select("SELECT COUNT(*) FROM infusion_order WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /** 更新执行状态 */
    @Update("UPDATE infusion_order SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新缴费状态 */
    @Update("UPDATE infusion_order SET pay_status = #{payStatus} WHERE id = #{id}")
    int updatePayStatus(@Param("id") Long id, @Param("payStatus") String payStatus);

    /** 查询患者未缴费输液单列表 */
    @Select("SELECT * FROM infusion_order WHERE patient_id = #{patientId} AND pay_status = 'UNPAID' ORDER BY create_time DESC")
    List<InfusionOrder> selectUnpaidByPatient(@Param("patientId") Long patientId);
}
