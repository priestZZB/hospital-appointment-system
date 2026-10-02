package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ExamReservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 检查预约表 Mapper（注解式，D1）
 */
@Mapper
public interface ExamReservationMapper {

    /** 插入预约记录（useGeneratedKeys 回填主键，Oracle 必须显式 keyColumn） */
    @Insert("INSERT INTO exam_reservation (application_id, patient_id, reserve_date, time_slot, " +
            "room, status, create_time) " +
            "VALUES (#{applicationId}, #{patientId}, #{reserveDate}, #{timeSlot}, " +
            "#{room}, #{status}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ExamReservation reservation);

    /** 根据主键查询 */
    @Select("SELECT * FROM exam_reservation WHERE id = #{id}")
    @Results(id = "reservationMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "applicationId", column = "application_id"),
            @Result(property = "patientId", column = "patient_id"),
            @Result(property = "reserveDate", column = "reserve_date"),
            @Result(property = "timeSlot", column = "time_slot"),
            @Result(property = "room", column = "room"),
            @Result(property = "status", column = "status"),
            @Result(property = "checkinTime", column = "checkin_time"),
            @Result(property = "createTime", column = "create_time")
    })
    ExamReservation selectById(@Param("id") Long id);

    /** 按检查申请查询预约记录（判定重复预约用） */
    @Select("SELECT * FROM exam_reservation WHERE application_id = #{applicationId} " +
            "ORDER BY create_time DESC, id DESC")
    @ResultMap("reservationMap")
    List<ExamReservation> selectByApplication(@Param("applicationId") Long applicationId);

    /** 分页查询（预约日期/状态可选，按日期倒序） */
    @Select("<script>" +
            "SELECT * FROM exam_reservation WHERE 1=1 " +
            "<if test='reserveDate != null'> AND reserve_date = #{reserveDate} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "ORDER BY reserve_date DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @ResultMap("reservationMap")
    List<ExamReservation> selectByPage(@Param("reserveDate") LocalDate reserveDate,
                                       @Param("status") String status,
                                       @Param("offset") Integer offset,
                                       @Param("limit") Integer limit);

    /** 分页统计预约数量（条件与 selectByPage 一致） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM exam_reservation WHERE 1=1 " +
            "<if test='reserveDate != null'> AND reserve_date = #{reserveDate} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "</script>")
    long countPage(@Param("reserveDate") LocalDate reserveDate, @Param("status") String status);

    /**
     * 更新预约状态
     * <p>
     * BOOKED → CHECKED_IN 时写入 checkin_time；DONE/CANCELLED 不动 checkin_time（传 null）。
     */
    @Update("UPDATE exam_reservation SET status = #{status}, " +
            "checkin_time = CASE WHEN #{checkinTime} IS NOT NULL THEN #{checkinTime} ELSE checkin_time END " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("checkinTime") LocalDateTime checkinTime);

    /** 患者自助改约（迭代13 K4）：仅 BOOKED 状态可改，重写预约日期与时段 */
    @Update("UPDATE exam_reservation SET reserve_date = #{reserveDate}, time_slot = #{timeSlot} " +
            "WHERE id = #{id} AND status = 'BOOKED'")
    int reschedule(@Param("id") Long id, @Param("reserveDate") LocalDate reserveDate,
                   @Param("timeSlot") String timeSlot);
}
