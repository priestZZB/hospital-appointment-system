package com.hospital.payment.mapper;

import com.hospital.payment.entity.InsuranceSettle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 医保结算单 Mapper（注解式，迭代11 H2）。
 * <p>
 * 结算单号 MI + yyyyMMdd + 6 位流水（seq_settle_no 取号，同迭代 10 surgery 模式）。
 */
@Mapper
public interface InsuranceSettleMapper {

    String COLS = "id, settle_no, patient_id, insurance_no, biz_type, biz_ref_id, total_amount, " +
            "catalog_a_amount, catalog_b_amount, self_amount, insurance_pay, personal_account_pay, " +
            "cash_amount, detail_json, status, operator_id, create_time";

    /** 生成医保结算单号：MI + yyyyMMdd + 6 位流水（seq_settle_no，V9 迁移创建） */
    @Select("SELECT 'MI' || TO_CHAR(SYSDATE, 'YYYYMMDD') || LPAD(TO_CHAR(seq_settle_no.NEXTVAL), 6, '0') FROM dual")
    String nextSettleNo();

    @Insert("INSERT INTO insurance_settle (settle_no, patient_id, insurance_no, biz_type, biz_ref_id, " +
            "total_amount, catalog_a_amount, catalog_b_amount, self_amount, insurance_pay, " +
            "personal_account_pay, cash_amount, detail_json, status, operator_id, create_time) " +
            "VALUES (#{settleNo}, #{patientId}, #{insuranceNo}, #{bizType}, #{bizRefId}, " +
            "#{totalAmount}, #{catalogAAmount}, #{catalogBAmount}, #{selfAmount}, #{insurancePay}, " +
            "#{personalAccountPay}, #{cashAmount}, #{detailJson}, NVL(#{status}, 'SETTLED'), #{operatorId}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InsuranceSettle settle);

    @Select("SELECT " + COLS + " FROM insurance_settle WHERE id = #{id}")
    InsuranceSettle selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM insurance_settle WHERE 1=1 " +
            "<if test='patientId != null'> AND patient_id = #{patientId} </if>" +
            "<if test='bizType != null and bizType != &quot;&quot;'> AND biz_type = #{bizType} </if>" +
            " ORDER BY create_time DESC, id DESC" +
            " OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</script>")
    List<InsuranceSettle> selectPage(@Param("patientId") Long patientId, @Param("bizType") String bizType,
                                     @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM insurance_settle WHERE 1=1 " +
            "<if test='patientId != null'> AND patient_id = #{patientId} </if>" +
            "<if test='bizType != null and bizType != &quot;&quot;'> AND biz_type = #{bizType} </if>" +
            "</script>")
    long countPage(@Param("patientId") Long patientId, @Param("bizType") String bizType);

    /** 冲正：SETTLED → REVERSED（乐观更新，affected rows = 0 即状态不允许） */
    @Update("UPDATE insurance_settle SET status = 'REVERSED' WHERE id = #{id} AND status = 'SETTLED'")
    int reverse(@Param("id") Long id);
}
