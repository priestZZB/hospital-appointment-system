package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.SurgeryApply;
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

/** 手术申请 Mapper（注解式；排台/执行在迭代10 扩展） */
@Mapper
public interface SurgeryApplyMapper {

    String COLS = "id, admission_id, patient_id, surgery_name, anesthesia_type, apply_doctor_id, " +
            "scheduled_time, operating_room, status, remark, create_time, update_time";

    @Insert("INSERT INTO surgery_apply (admission_id, patient_id, surgery_name, anesthesia_type, " +
            "apply_doctor_id, status, remark, create_time, update_time) " +
            "VALUES (#{admissionId}, #{patientId}, #{surgeryName}, #{anesthesiaType}, " +
            "#{applyDoctorId}, 'PENDING', #{remark}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(SurgeryApply apply);

    @Results(id = "surgeryMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "surgery_name", property = "surgeryName"),
            @Result(column = "anesthesia_type", property = "anesthesiaType"),
            @Result(column = "apply_doctor_id", property = "applyDoctorId"),
            @Result(column = "scheduled_time", property = "scheduledTime"),
            @Result(column = "operating_room", property = "operatingRoom"),
            @Result(column = "status", property = "status"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM surgery_apply WHERE id = #{id}")
    SurgeryApply selectById(@Param("id") Long id);

    @ResultMap("surgeryMap")
    @Select("<script>SELECT " + COLS + " FROM surgery_apply WHERE 1=1 " +
            "<if test='admissionId != null'> AND admission_id = #{admissionId} </if>" +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "ORDER BY create_time DESC FETCH FIRST 200 ROWS ONLY</script>")
    List<SurgeryApply> selectList(@Param("admissionId") Long admissionId, @Param("status") String status);

    /** 排台（PENDING -> SCHEDULED） */
    @Update("UPDATE surgery_apply SET status = 'SCHEDULED', scheduled_time = #{scheduledTime}, " +
            "operating_room = #{operatingRoom}, update_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int schedule(@Param("id") Long id, @Param("scheduledTime") java.time.LocalDateTime scheduledTime,
                 @Param("operatingRoom") String operatingRoom);

    /** 取消（PENDING/SCHEDULED -> CANCELLED） */
    @Update("UPDATE surgery_apply SET status = 'CANCELLED', update_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('PENDING','SCHEDULED')")
    int cancel(@Param("id") Long id);
}
