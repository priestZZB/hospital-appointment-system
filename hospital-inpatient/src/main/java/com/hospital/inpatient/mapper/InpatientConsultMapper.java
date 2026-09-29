package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.InpatientConsult;
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

/** 住院院内会诊 Mapper（注解式） */
@Mapper
public interface InpatientConsultMapper {

    String COLS = "id, admission_id, patient_id, request_dept_id, request_doctor_id, target_dept_id, " +
            "target_doctor_id, reason, opinion, status, handle_doctor_id, handle_time, create_time, update_time";

    @Insert("INSERT INTO inpatient_consult (admission_id, patient_id, request_dept_id, request_doctor_id, " +
            "target_dept_id, target_doctor_id, reason, opinion, status, create_time, update_time) " +
            "VALUES (#{admissionId}, #{patientId}, #{requestDeptId}, #{requestDoctorId}, #{targetDeptId}, " +
            "#{targetDoctorId}, #{reason}, #{opinion}, 'PENDING', SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InpatientConsult consult);

    @Results(id = "consultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "request_dept_id", property = "requestDeptId"),
            @Result(column = "request_doctor_id", property = "requestDoctorId"),
            @Result(column = "target_dept_id", property = "targetDeptId"),
            @Result(column = "target_doctor_id", property = "targetDoctorId"),
            @Result(column = "reason", property = "reason"),
            @Result(column = "opinion", property = "opinion"),
            @Result(column = "status", property = "status"),
            @Result(column = "handle_doctor_id", property = "handleDoctorId"),
            @Result(column = "handle_time", property = "handleTime"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM inpatient_consult WHERE id = #{id}")
    InpatientConsult selectById(@Param("id") Long id);

    @ResultMap("consultMap")
    @Select("<script>SELECT " + COLS + " FROM inpatient_consult WHERE 1=1 " +
            "<if test='admissionId != null'> AND admission_id = #{admissionId} </if>" +
            "<if test='targetDeptId != null'> AND target_dept_id = #{targetDeptId} </if>" +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "ORDER BY create_time DESC FETCH FIRST 200 ROWS ONLY</script>")
    List<InpatientConsult> selectList(@Param("admissionId") Long admissionId,
                                      @Param("targetDeptId") Long targetDeptId,
                                      @Param("status") String status);

    /** 会诊处理（仅 PENDING 可处理） */
    @Update("UPDATE inpatient_consult SET status = #{status}, opinion = #{opinion}, " +
            "handle_doctor_id = #{handleDoctorId}, handle_time = SYSDATE, update_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int handle(@Param("id") Long id, @Param("status") String status,
               @Param("opinion") String opinion, @Param("handleDoctorId") Long handleDoctorId);
}
