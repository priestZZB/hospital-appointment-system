package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.FollowUpPlan;
import com.hospital.clinic.vo.FollowUpPlanVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/** 随访计划 Mapper（注解式） */
@Mapper
public interface FollowUpPlanMapper {

    @Insert("INSERT INTO follow_up_plan (patient_id, medical_record_id, doctor_id, follow_date, follow_method, template, status, create_time, update_time) " +
            "VALUES (#{patientId}, #{medicalRecordId}, #{doctorId}, #{followDate}, #{followMethod}, #{template}, 'PENDING', NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FollowUpPlan plan);

    @Select("SELECT id, patient_id, medical_record_id, doctor_id, follow_date, follow_method, template, status, create_time, update_time " +
            "FROM follow_up_plan WHERE id = #{id}")
    FollowUpPlan selectById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT p.id, p.patient_id, p.medical_record_id, p.doctor_id, p.follow_date, p.follow_method, p.template, p.status, p.create_time, p.update_time, " +
            "d.name AS doctor_name " +
            "FROM follow_up_plan p LEFT JOIN doctor d ON p.doctor_id = d.id " +
            "WHERE 1=1 " +
            "<if test='doctorId != null'> AND p.doctor_id = #{doctorId} </if>" +
            "<if test='patientId != null'> AND p.patient_id = #{patientId} </if>" +
            "<if test='status != null and status != \"\"'> AND p.status = #{status} </if>" +
            "ORDER BY p.follow_date DESC, p.create_time DESC" +
            "</script>")
    List<FollowUpPlanVO> selectList(@Param("doctorId") Long doctorId,
                                    @Param("patientId") Long patientId,
                                    @Param("status") String status);

    @Update("UPDATE follow_up_plan SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
