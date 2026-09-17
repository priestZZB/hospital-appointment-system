package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.ReferralOrder;
import com.hospital.clinic.vo.ReferralOrderVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 转诊单 Mapper（注解式） */
@Mapper
public interface ReferralOrderMapper {

    @Insert("INSERT INTO referral_order (referral_no, medical_record_id, patient_id, from_dept_id, from_doctor_id, " +
            "to_dept_id, reason, status, create_time, update_time) " +
            "VALUES (#{referralNo}, #{medicalRecordId}, #{patientId}, #{fromDeptId}, #{fromDoctorId}, " +
            "#{toDeptId}, #{reason}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReferralOrder referral);

    @Select("SELECT id, referral_no, medical_record_id, patient_id, from_dept_id, from_doctor_id, " +
            "to_dept_id, reason, status, accept_time, complete_time, create_time, update_time " +
            "FROM referral_order WHERE id = #{id}")
    ReferralOrder selectById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT r.id, r.referral_no, r.medical_record_id, r.patient_id, r.from_dept_id, r.from_doctor_id, " +
            "r.to_dept_id, r.reason, r.status, r.accept_time, r.complete_time, r.create_time, r.update_time, " +
            "fd.dept_name AS from_dept_name, td.dept_name AS to_dept_name, " +
            "fdoc.name AS from_doctor_name, tdoc.name AS to_doctor_name " +
            "FROM referral_order r " +
            "LEFT JOIN department fd ON r.from_dept_id = fd.id " +
            "LEFT JOIN department td ON r.to_dept_id = td.id " +
            "LEFT JOIN doctor fdoc ON r.from_doctor_id = fdoc.id " +
            "LEFT JOIN doctor tdoc ON r.to_dept_id = tdoc.id " +
            "WHERE 1=1 " +
            "<if test='fromDoctorId != null'> AND r.from_doctor_id = #{fromDoctorId} </if>" +
            "<if test='toDeptId != null'> AND r.to_dept_id = #{toDeptId} </if>" +
            "<if test='patientId != null'> AND r.patient_id = #{patientId} </if>" +
            "<if test='status != null and status != \"\"'> AND r.status = #{status} </if>" +
            "ORDER BY r.create_time DESC" +
            "</script>")
    List<ReferralOrderVO> selectList(@Param("fromDoctorId") Long fromDoctorId,
                                     @Param("toDeptId") Long toDeptId,
                                     @Param("patientId") Long patientId,
                                     @Param("status") String status);

    @Select("SELECT r.id, r.referral_no, r.medical_record_id, r.patient_id, r.from_dept_id, r.from_doctor_id, " +
            "r.to_dept_id, r.reason, r.status, r.accept_time, r.complete_time, r.create_time, r.update_time, " +
            "fd.dept_name AS from_dept_name, td.dept_name AS to_dept_name, " +
            "fdoc.name AS from_doctor_name, tdoc.name AS to_doctor_name " +
            "FROM referral_order r " +
            "LEFT JOIN department fd ON r.from_dept_id = fd.id " +
            "LEFT JOIN department td ON r.to_dept_id = td.id " +
            "LEFT JOIN doctor fdoc ON r.from_doctor_id = fdoc.id " +
            "LEFT JOIN doctor tdoc ON r.to_dept_id = tdoc.id " +
            "WHERE r.id = #{id}")
    ReferralOrderVO selectVOById(@Param("id") Long id);

    @Update("UPDATE referral_order SET status = #{status}, accept_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateAccept(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE referral_order SET status = 'COMPLETED', complete_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateComplete(@Param("id") Long id);
}
