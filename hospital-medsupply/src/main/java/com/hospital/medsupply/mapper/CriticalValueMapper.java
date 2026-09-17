package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.CriticalValue;
import com.hospital.medsupply.vo.CriticalValueVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 危急值 Mapper（注解式） */
@Mapper
public interface CriticalValueMapper {

    @Insert("INSERT INTO critical_value (report_id, application_id, patient_id, item_name, result_value, " +
            "reference_range, critical_level, status, reporter_id, create_time, update_time) " +
            "VALUES (#{reportId}, #{applicationId}, #{patientId}, #{itemName}, #{resultValue}, " +
            "#{referenceRange}, #{criticalLevel}, 'PENDING', #{reporterId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CriticalValue value);

    @Select("SELECT id, report_id, application_id, patient_id, item_name, result_value, reference_range, " +
            "critical_level, status, reporter_id, confirm_doctor_id, confirm_comment, confirm_time, create_time, update_time " +
            "FROM critical_value WHERE id = #{id}")
    CriticalValue selectById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT v.id, v.report_id, v.application_id, v.patient_id, v.item_name, v.result_value, v.reference_range, " +
            "v.critical_level, v.status, v.reporter_id, v.confirm_doctor_id, v.confirm_comment, v.confirm_time, v.create_time " +
            "FROM critical_value v " +
            "WHERE 1=1 " +
            "<if test='status != null and status != \"\"'> AND v.status = #{status} </if>" +
            "<if test='patientId != null'> AND v.patient_id = #{patientId} </if>" +
            "ORDER BY v.create_time DESC" +
            "</script>")
    List<CriticalValueVO> selectList(@Param("status") String status, @Param("patientId") Long patientId);

    @Update("UPDATE critical_value SET status = #{status}, confirm_doctor_id = #{doctorId}, confirm_comment = #{comment}, " +
            "confirm_time = NOW(), update_time = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int confirm(@Param("id") Long id, @Param("status") String status,
                @Param("doctorId") Long doctorId, @Param("comment") String comment);
}
