package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DoctorAntibioticAuth;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 医生抗菌药物分级授权 Mapper（注解式）
 */
@Mapper
public interface DoctorAntibioticAuthMapper {

    /**
     * 授权（upsert：doctor_id 唯一约束，存在则更新分级并重新生效）
     * <p>
     * Oracle MERGE 写法，幂等。
     */
    @Insert("MERGE INTO doctor_antibiotic_auth t " +
            "USING (SELECT #{doctorId} AS doctor_id FROM dual) s ON (t.doctor_id = s.doctor_id) " +
            "WHEN MATCHED THEN UPDATE SET t.max_level = #{maxLevel}, t.approver_id = #{approverId}, t.status = 1 " +
            "WHEN NOT MATCHED THEN INSERT (doctor_id, max_level, approver_id, status) " +
            "VALUES (#{doctorId}, #{maxLevel}, #{approverId}, 1)")
    int upsert(@Param("doctorId") Long doctorId,
               @Param("maxLevel") String maxLevel,
               @Param("approverId") Long approverId);

    /** 查询医生有效授权（status=1） */
    @Select("SELECT * FROM doctor_antibiotic_auth WHERE doctor_id = #{doctorId} AND status = 1")
    @Results(id = "antibioticAuthMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "doctorId", column = "doctor_id"),
            @Result(property = "maxLevel", column = "max_level"),
            @Result(property = "approverId", column = "approver_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time")
    })
    DoctorAntibioticAuth selectByDoctorId(@Param("doctorId") Long doctorId);

    /** 查询全部授权（倒序） */
    @Select("SELECT * FROM doctor_antibiotic_auth ORDER BY create_time DESC, id DESC")
    @ResultMap("antibioticAuthMap")
    List<DoctorAntibioticAuth> selectAll();

    /** 撤销授权（status=0） */
    @Update("UPDATE doctor_antibiotic_auth SET status = 0 WHERE doctor_id = #{doctorId}")
    int revoke(@Param("doctorId") Long doctorId);
}
