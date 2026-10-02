package com.hospital.medsupply.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 设备台账 Mapper（迭代14 L2，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface EquipmentMapper {

    String COLS = "e.id AS \"id\", e.code AS \"code\", e.name AS \"name\", e.model AS \"model\", "
            + "e.location AS \"location\", e.status AS \"status\", "
            + "TO_CHAR(e.buy_date, 'YYYY-MM-DD') AS \"buyDate\", "
            + "TO_CHAR(e.last_maintain_date, 'YYYY-MM-DD') AS \"lastMaintainDate\", e.remark AS \"remark\"";

    @Insert("INSERT INTO equipment (code, name, model, location, buy_date, remark) " +
            "VALUES (#{code}, #{name}, #{model}, #{location}, TO_DATE(#{buyDate}, 'YYYY-MM-DD'), #{remark})")
    int insert(@Param("code") String code, @Param("name") String name, @Param("model") String model,
               @Param("location") String location, @Param("buyDate") String buyDate, @Param("remark") String remark);

    @Select("SELECT " + COLS + " FROM equipment e WHERE e.id = #{id}")
    Map<String, Object> selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM equipment e " +
            "<where>" +
            "  <if test='status != null'> AND e.status = #{status}</if>" +
            "  <if test='keyword != null'> AND (e.name LIKE '%'||#{keyword}||'%' OR e.code LIKE '%'||#{keyword}||'%' OR e.location LIKE '%'||#{keyword}||'%')</if>" +
            "</where> ORDER BY e.id OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("status") String status, @Param("keyword") String keyword,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM equipment e " +
            "<where>" +
            "  <if test='status != null'> AND e.status = #{status}</if>" +
            "  <if test='keyword != null'> AND (e.name LIKE '%'||#{keyword}||'%' OR e.code LIKE '%'||#{keyword}||'%' OR e.location LIKE '%'||#{keyword}||'%')</if>" +
            "</where></script>")
    long countPage(@Param("status") String status, @Param("keyword") String keyword);

    /** 设备状态变更 */
    @Update("UPDATE equipment SET status = #{status} WHERE id = #{id} AND status != 'SCRAP'")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 维保登记 */
    @Update("UPDATE equipment SET status = 'IDLE', last_maintain_date = TO_DATE(#{maintainDate}, 'YYYY-MM-DD') " +
            "WHERE id = #{id} AND status != 'SCRAP'")
    int maintain(@Param("id") Long id, @Param("maintainDate") String maintainDate);
}
