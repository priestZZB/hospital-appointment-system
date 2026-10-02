package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 考勤打卡 Mapper（迭代14 L4，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface AttendanceMapper {

    @Insert("INSERT INTO attendance (user_id, user_name, work_date, checkin_time, status, remark) " +
            "VALUES (#{userId}, #{userName}, TO_DATE(#{workDate}, 'YYYY-MM-DD'), SYSDATE, 'ON_DUTY', #{remark})")
    int checkin(@Param("userId") Long userId, @Param("userName") String userName,
                @Param("workDate") String workDate, @Param("remark") String remark);

    /** 当日已打卡（幂等判断） */
    @Select("SELECT id AS \"id\", status AS \"status\", " +
            "  TO_CHAR(checkin_time, 'YYYY-MM-DD HH24:MI') AS \"checkinTime\", " +
            "  TO_CHAR(checkout_time, 'YYYY-MM-DD HH24:MI') AS \"checkoutTime\" " +
            "FROM attendance WHERE user_id = #{userId} AND work_date = TO_DATE(#{workDate}, 'YYYY-MM-DD')")
    Map<String, Object> selectByUserDate(@Param("userId") Long userId, @Param("workDate") String workDate);

    /** 签退 */
    @Update("UPDATE attendance SET checkout_time = SYSDATE, status = 'OFF_DUTY' " +
            "WHERE user_id = #{userId} AND work_date = TO_DATE(#{workDate}, 'YYYY-MM-DD') AND status = 'ON_DUTY'")
    int checkout(@Param("userId") Long userId, @Param("workDate") String workDate);

    @Select("<script>SELECT a.id AS \"id\", a.user_id AS \"userId\", a.user_name AS \"userName\", " +
            "  TO_CHAR(a.work_date, 'YYYY-MM-DD') AS \"workDate\", " +
            "  TO_CHAR(a.checkin_time, 'YYYY-MM-DD HH24:MI') AS \"checkinTime\", " +
            "  TO_CHAR(a.checkout_time, 'YYYY-MM-DD HH24:MI') AS \"checkoutTime\", a.status AS \"status\" " +
            "FROM attendance a " +
            "<where>" +
            "  <if test='userId != null'> AND a.user_id = #{userId}</if>" +
            "  <if test='date != null'> AND a.work_date = TO_DATE(#{date}, 'YYYY-MM-DD')</if>" +
            "</where> ORDER BY a.work_date DESC, a.id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("userId") Long userId, @Param("date") String date,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM attendance a " +
            "<where>" +
            "  <if test='userId != null'> AND a.user_id = #{userId}</if>" +
            "  <if test='date != null'> AND a.work_date = TO_DATE(#{date}, 'YYYY-MM-DD')</if>" +
            "</where></script>")
    long countPage(@Param("userId") Long userId, @Param("date") String date);

    /** 按人统计出勤天数（近 30 天） */
    @Select("SELECT user_id AS \"userId\", user_name AS \"userName\", COUNT(*) AS \"days\", " +
            "  SUM(CASE WHEN checkout_time IS NOT NULL THEN 1 ELSE 0 END) AS \"fullDays\" " +
            "FROM attendance WHERE work_date >= TRUNC(SYSDATE) - 30 " +
            "GROUP BY user_id, user_name ORDER BY user_id")
    List<Map<String, Object>> summary();
}
