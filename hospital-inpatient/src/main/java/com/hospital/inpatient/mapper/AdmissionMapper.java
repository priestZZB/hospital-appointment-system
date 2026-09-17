package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.Admission;
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

/** 入院登记 Mapper（注解式） */
@Mapper
public interface AdmissionMapper {

    String COLS = "id, admission_no, patient_id, department_id, attending_doctor_id, attending_doctor_name, " +
            "admission_diag, expected_days, admission_time, status, create_time, update_time";

    @Results(id = "admissionMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_no", property = "admissionNo"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "department_id", property = "departmentId"),
            @Result(column = "attending_doctor_id", property = "attendingDoctorId"),
            @Result(column = "attending_doctor_name", property = "attendingDoctorName"),
            @Result(column = "admission_diag", property = "admissionDiag"),
            @Result(column = "expected_days", property = "expectedDays"),
            @Result(column = "admission_time", property = "admissionTime"),
            @Result(column = "status", property = "status"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM admission WHERE id = #{id}")
    Admission selectById(@Param("id") Long id);

    @Insert("INSERT INTO admission (admission_no, patient_id, department_id, attending_doctor_id, attending_doctor_name, " +
            "admission_diag, expected_days, admission_time, status, create_time, update_time) " +
            "VALUES (#{admissionNo}, #{patientId}, #{departmentId}, #{attendingDoctorId}, #{attendingDoctorName}, " +
            "#{admissionDiag}, #{expectedDays}, NOW(), 'ADMITTED', NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Admission admission);

    @ResultMap("admissionMap")
    @Select("<script>SELECT " + COLS + " FROM admission WHERE 1=1 " +
            "<if test='departmentId != null'> AND department_id = #{departmentId} </if>" +
            "<if test='patientId != null'> AND patient_id = #{patientId} </if>" +
            "<if test='doctorId != null'> AND attending_doctor_id = #{doctorId} </if>" +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "ORDER BY admission_time DESC LIMIT #{offset}, #{limit}</script>")
    List<Admission> selectList(@Param("departmentId") Long departmentId,
                               @Param("patientId") Long patientId,
                               @Param("doctorId") Long doctorId,
                               @Param("status") String status,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Update("UPDATE admission SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 转科（E1）：更新收治科室 */
    @Update("UPDATE admission SET department_id = #{deptId}, update_time = NOW() WHERE id = #{id}")
    int updateDepartment(@Param("id") Long id, @Param("deptId") Long deptId);

    @ResultMap("admissionMap")
    @Select("SELECT " + COLS + " FROM admission WHERE patient_id = #{patientId} AND status = 'ADMITTED' LIMIT 1")
    Admission selectActiveByPatient(@Param("patientId") Long patientId);
}
