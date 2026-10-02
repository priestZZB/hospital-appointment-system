package com.hospital.medsupply.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 耗材管理 Mapper（迭代14 L1，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface ConsumableMapper {

    String COLS = "c.id AS \"id\", c.code AS \"code\", c.name AS \"name\", c.specification AS \"specification\", "
            + "c.unit AS \"unit\", c.price AS \"price\", c.stock AS \"stock\", c.safety_stock AS \"safetyStock\", "
            + "c.status AS \"status\", TO_CHAR(c.create_time, 'YYYY-MM-DD') AS \"createTime\"";

    @Insert("INSERT INTO consumable (code, name, specification, unit, price, stock, safety_stock) " +
            "VALUES (#{code}, #{name}, #{specification}, #{unit}, #{price}, #{stock}, #{safetyStock})")
    int insert(@Param("code") String code, @Param("name") String name,
               @Param("specification") String specification, @Param("unit") String unit,
               @Param("price") Double price, @Param("stock") Integer stock, @Param("safetyStock") Integer safetyStock);

    @Select("SELECT id FROM consumable WHERE code = #{code}")
    Long selectIdByCode(@Param("code") String code);

    @Select("SELECT stock FROM consumable WHERE id = #{id} FOR UPDATE")
    Integer selectStockForUpdate(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM consumable c " +
            "<where>" +
            "  <if test='status != null'> AND c.status = #{status}</if>" +
            "  <if test='keyword != null'> AND (c.name LIKE '%'||#{keyword}||'%' OR c.code LIKE '%'||#{keyword}||'%')</if>" +
            "</where> ORDER BY c.id OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("status") String status, @Param("keyword") String keyword,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM consumable c " +
            "<where>" +
            "  <if test='status != null'> AND c.status = #{status}</if>" +
            "  <if test='keyword != null'> AND (c.name LIKE '%'||#{keyword}||'%' OR c.code LIKE '%'||#{keyword}||'%')</if>" +
            "</where></script>")
    long countPage(@Param("status") String status, @Param("keyword") String keyword);

    /** 出入库（IN 加库存 / OUT 减库存并校验不足） */
    @Update("<script>UPDATE consumable SET stock = stock + #{delta} WHERE id = #{id}" +
            "<if test='requireNonNegative'> AND stock + #{delta} &gt;= 0</if></script>")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta, @Param("requireNonNegative") boolean requireNonNegative);

    @Insert("INSERT INTO consumable_record (consumable_id, record_type, quantity, operator_id, operator_name, remark) " +
            "VALUES (#{consumableId}, #{recordType}, #{quantity}, #{operatorId}, #{operatorName}, #{remark})")
    int insertRecord(@Param("consumableId") Long consumableId, @Param("recordType") String recordType,
                     @Param("quantity") int quantity, @Param("operatorId") Long operatorId,
                     @Param("operatorName") String operatorName, @Param("remark") String remark);

    /** 出入库流水分页 */
    @Select("SELECT r.id AS \"id\", r.consumable_id AS \"consumableId\", c.name AS \"consumableName\", " +
            "  r.record_type AS \"recordType\", r.quantity AS \"quantity\", " +
            "  r.operator_name AS \"operatorName\", r.remark AS \"remark\", " +
            "  TO_CHAR(r.create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM consumable_record r JOIN consumable c ON c.id = r.consumable_id " +
            "ORDER BY r.create_time DESC, r.id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY")
    List<Map<String, Object>> selectRecordPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM consumable_record")
    long countRecordPage();

    /** 低于安全库存的耗材（预警） */
    @Select("SELECT " + COLS + " FROM consumable c WHERE c.status = 'ACTIVE' AND c.stock <= c.safety_stock ORDER BY c.id")
    List<Map<String, Object>> selectLowStock();
}
