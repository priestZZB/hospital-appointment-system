package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.Bed;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 占床记录 Mapper（注解式） */
@Mapper
public interface BedOccupancyMapper {

    @Insert("INSERT INTO bed_occupancy (admission_id, bed_id, start_time, change_type, create_time) " +
            "VALUES (#{admissionId}, #{bedId}, NOW(), #{changeType}, NOW())")
    int insert(@Param("admissionId") Long admissionId,
               @Param("bedId") Long bedId,
               @Param("changeType") String changeType);

    @Update("UPDATE bed_occupancy SET end_time = NOW() " +
            "WHERE admission_id = #{admissionId} AND end_time IS NULL")
    int closeCurrent(@Param("admissionId") Long admissionId);

    /** 查询某住院患者当前占用的床位（未退房） */
    @Results(id = "currentBedMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "department_id", property = "departmentId"),
            @Result(column = "room_no", property = "roomNo"),
            @Result(column = "bed_no", property = "bedNo"),
            @Result(column = "bed_type", property = "bedType"),
            @Result(column = "daily_fee", property = "dailyFee"),
            @Result(column = "status", property = "status")
    })
    @Select("SELECT b.id, b.department_id, b.room_no, b.bed_no, b.bed_type, b.daily_fee, b.status " +
            "FROM bed b JOIN bed_occupancy o ON o.bed_id = b.id " +
            "WHERE o.admission_id = #{admissionId} AND o.end_time IS NULL LIMIT 1")
    Bed selectCurrentBed(@Param("admissionId") Long admissionId);

    /** 按床位批量查当前占用者（床卡看板） */
    @Select("SELECT o.bed_id FROM bed_occupancy o WHERE o.end_time IS NULL AND o.bed_id IN " +
            "<foreach item='i' collection='bedIds' open='(' separator=',' close=')'>#{i}</foreach>")
    List<Long> selectOccupiedBedIds(@Param("bedIds") List<Long> bedIds);

    /** 出院结算用：住院天数（按占床记录起算，无记录则从入院时间算） */
    @Select("SELECT COALESCE(DATEDIFF(#{endTime}, MIN(o.start_time)), 0) FROM bed_occupancy o WHERE o.admission_id = #{admissionId}")
    Integer daysOccupied(@Param("admissionId") Long admissionId, @Param("endTime") LocalDateTime endTime);
    @Select("SELECT o.bed_id AS bedId, a.id AS admissionId, a.admission_no AS admissionNo, a.patient_id AS patientId FROM bed_occupancy o JOIN admission a ON a.id = o.admission_id WHERE o.end_time IS NULL AND a.department_id = #{departmentId} AND a.status = 'ADMITTED'")
    java.util.List<java.util.Map<String, Object>> selectCurrentOccupants(@Param("departmentId") Long departmentId);
}