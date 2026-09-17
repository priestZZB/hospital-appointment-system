package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.Bed;
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

/** 床位 Mapper（注解式） */
@Mapper
public interface BedMapper {

    String COLS = "id, department_id, room_no, bed_no, bed_type, daily_fee, status, create_time, update_time";

    @Results(id = "bedMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "department_id", property = "departmentId"),
            @Result(column = "room_no", property = "roomNo"),
            @Result(column = "bed_no", property = "bedNo"),
            @Result(column = "bed_type", property = "bedType"),
            @Result(column = "daily_fee", property = "dailyFee"),
            @Result(column = "status", property = "status"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM bed WHERE id = #{id}")
    Bed selectById(@Param("id") Long id);

    @ResultMap("bedMap")
    @Select("<script>SELECT " + COLS + " FROM bed WHERE 1=1 " +
            "<if test='departmentId != null'> AND department_id = #{departmentId} </if>" +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "ORDER BY room_no, bed_no</script>")
    List<Bed> selectList(@Param("departmentId") Long departmentId, @Param("status") String status);

    @Insert("INSERT INTO bed (department_id, room_no, bed_no, bed_type, daily_fee, status, create_time, update_time) " +
            "VALUES (#{departmentId}, #{roomNo}, #{bedNo}, #{bedType}, #{dailyFee}, 'AVAILABLE', NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Bed bed);

    /** 乐观占用床位：仅当床位当前空闲才能置为占用，防并发抢同一床 */
    @Update("UPDATE bed SET status = 'OCCUPIED', update_time = NOW() WHERE id = #{id} AND status = 'AVAILABLE'")
    int occupyIfAvailable(@Param("id") Long id);

    @Update("UPDATE bed SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE bed SET status = 'AVAILABLE', update_time = NOW() WHERE status = 'OCCUPIED' " +
            "AND id IN (SELECT bed_id FROM bed_occupancy WHERE admission_id = #{admissionId} AND end_time IS NULL)")
    int releaseByAdmission(@Param("admissionId") Long admissionId);
}
