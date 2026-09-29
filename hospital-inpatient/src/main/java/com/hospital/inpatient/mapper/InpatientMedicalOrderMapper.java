package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.InpatientMedicalOrder;
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

/** 住院医嘱 Mapper（注解式） */
@Mapper
public interface InpatientMedicalOrderMapper {

    String COLS = "id, order_no, admission_id, doctor_id, order_type, category, content, frequency, " +
            "status, open_time, confirm_time, confirm_nurse_id, stop_time, create_time, update_time";

    @Results(id = "orderMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_no", property = "orderNo"),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "doctor_id", property = "doctorId"),
            @Result(column = "order_type", property = "orderType"),
            @Result(column = "category", property = "category"),
            @Result(column = "content", property = "content"),
            @Result(column = "frequency", property = "frequency"),
            @Result(column = "status", property = "status"),
            @Result(column = "open_time", property = "openTime"),
            @Result(column = "confirm_time", property = "confirmTime"),
            @Result(column = "confirm_nurse_id", property = "confirmNurseId"),
            @Result(column = "stop_time", property = "stopTime"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM inpatient_medical_order WHERE id = #{id}")
    InpatientMedicalOrder selectById(@Param("id") Long id);

    @Insert("INSERT INTO inpatient_medical_order (order_no, admission_id, doctor_id, order_type, category, " +
            "content, frequency, status, open_time, create_time, update_time) " +
            "VALUES (#{orderNo}, #{admissionId}, #{doctorId}, #{orderType}, #{category}, " +
            "#{content}, #{frequency}, 'OPEN', SYSDATE, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InpatientMedicalOrder order);

    @ResultMap("orderMap")
    @Select("<script>SELECT " + COLS + " FROM inpatient_medical_order WHERE 1=1 " +
            "<if test='admissionId != null'> AND admission_id = #{admissionId} </if>" +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "<if test='statusList != null and statusList.size() > 0'> AND status IN " +
            "<foreach item='s' collection='statusList' open='(' separator=',' close=')'>#{s}</foreach> </if>" +
            "ORDER BY create_time DESC</script>")
    List<InpatientMedicalOrder> selectList(@Param("admissionId") Long admissionId,
                                           @Param("status") String status,
                                           @Param("statusList") List<String> statusList);

    /** 核对（仅 OPEN 可核对） */
    @Update("UPDATE inpatient_medical_order SET status = 'CONFIRMED', confirm_time = SYSDATE, confirm_nurse_id = #{nurseId}, " +
            "update_time = SYSDATE WHERE id = #{id} AND status = 'OPEN'")
    int confirm(@Param("id") Long id, @Param("nurseId") Long nurseId);

    /** 停止（OPEN/CONFIRMED/EXECUTING 可停止） */
    @Update("UPDATE inpatient_medical_order SET status = 'STOPPED', stop_time = SYSDATE, update_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('OPEN','CONFIRMED','EXECUTING')")
    int stop(@Param("id") Long id);

    @ResultMap("orderMap")
    @Select("SELECT " + COLS + " FROM inpatient_medical_order WHERE admission_id = #{admissionId} " +
            "AND order_type = 'LONG_TERM' AND status IN ('CONFIRMED','EXECUTING')")
    List<InpatientMedicalOrder> selectActiveLongTerm(@Param("admissionId") Long admissionId);

    /** 执行状态流转（CONFIRMED -> EXECUTING/COMPLETED，长期医嘱可反复执行） */
    @Update("UPDATE inpatient_medical_order SET status = #{status}, update_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('CONFIRMED','EXECUTING')")
    int updateExecStatus(@Param("id") Long id, @Param("status") String status);

    /** 按状态统计在院医嘱数（护士站/医生站看板） */
    @Select("SELECT COUNT(*) FROM inpatient_medical_order WHERE admission_id = #{admissionId} AND status = #{status}")
    long countByStatus(@Param("admissionId") Long admissionId, @Param("status") String status);
}
