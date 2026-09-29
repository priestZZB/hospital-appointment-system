package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.FollowUpRecord;
import com.hospital.clinic.vo.FollowUpRecordVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 随访记录 Mapper（注解式） */
@Mapper
public interface FollowUpRecordMapper {

    @Insert("INSERT INTO follow_up_record (plan_id, patient_id, doctor_id, content, next_follow_date, create_time) " +
            "VALUES (#{planId}, #{patientId}, #{doctorId}, #{content}, #{nextFollowDate}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(FollowUpRecord record);

    @Select("<script>" +
            "SELECT r.id, r.plan_id, r.patient_id, r.doctor_id, r.content, r.next_follow_date, r.create_time, " +
            "d.name AS doctor_name " +
            "FROM follow_up_record r LEFT JOIN doctor d ON r.doctor_id = d.id " +
            "WHERE r.plan_id = #{planId} ORDER BY r.create_time DESC" +
            "</script>")
    List<FollowUpRecordVO> selectByPlanId(@Param("planId") Long planId);
}
