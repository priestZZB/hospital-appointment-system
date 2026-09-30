package com.hospital.payment.mapper;

import com.hospital.payment.entity.ChargeItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 统一收费项目目录 Mapper（注解式，迭代11 H4）。
 * <p>
 * item_code 由 uk_charge_item_code 唯一约束兜底，服务层先查后插给出业务错误。
 */
@Mapper
public interface ChargeItemMapper {

    String COLS = "id, item_code, item_name, category, unit, unit_price, price_status, adjust_note, create_time, update_time";

    @Insert("INSERT INTO charge_item (item_code, item_name, category, unit, unit_price, price_status, adjust_note, create_time, update_time) " +
            "VALUES (#{itemCode}, #{itemName}, #{category}, #{unit}, #{unitPrice}, NVL(#{priceStatus}, 'ACTIVE'), #{adjustNote}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChargeItem item);

    /** 编辑（不含 price_status/adjust_note，调价走 adjustPrice 保持状态机清晰） */
    @Update("UPDATE charge_item SET item_name = #{itemName}, category = #{category}, unit = #{unit}, " +
            "unit_price = #{unitPrice}, adjust_note = #{adjustNote}, update_time = SYSDATE WHERE id = #{id}")
    int update(ChargeItem item);

    /** 调价：更新单价 + price_status=ADJUSTED + 调价说明（与 adjust_log 同事务由 Service 保证） */
    @Update("UPDATE charge_item SET unit_price = #{newPrice}, price_status = 'ADJUSTED', " +
            "adjust_note = #{reason}, update_time = SYSDATE WHERE id = #{id}")
    int adjustPrice(@Param("id") Long id, @Param("newPrice") BigDecimal newPrice, @Param("reason") String reason);

    /** 启用/停用（ACTIVE/DEPRECATED） */
    @Update("UPDATE charge_item SET price_status = #{status}, update_time = SYSDATE WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT " + COLS + " FROM charge_item WHERE id = #{id}")
    ChargeItem selectById(@Param("id") Long id);

    @Select("SELECT " + COLS + " FROM charge_item WHERE item_code = #{itemCode} FETCH FIRST 1 ROWS ONLY")
    ChargeItem selectByItemCode(@Param("itemCode") String itemCode);

    /** 分页（category 精确、keyword 模糊匹配编码或名称，大小写不敏感） */
    @Select("<script>SELECT " + COLS + " FROM charge_item WHERE 1=1 " +
            "<if test='category != null and category != &quot;&quot;'> AND category = #{category} </if>" +
            "<if test='keyword != null and keyword != &quot;&quot;'>" +
            " AND (UPPER(item_code) LIKE '%' || UPPER(#{keyword}) || '%' OR item_name LIKE '%' || #{keyword} || '%') </if>" +
            " ORDER BY update_time DESC, id DESC" +
            " OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</script>")
    List<ChargeItem> selectPage(@Param("category") String category, @Param("keyword") String keyword,
                                @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM charge_item WHERE 1=1 " +
            "<if test='category != null and category != &quot;&quot;'> AND category = #{category} </if>" +
            "<if test='keyword != null and keyword != &quot;&quot;'>" +
            " AND (UPPER(item_code) LIKE '%' || UPPER(#{keyword}) || '%' OR item_name LIKE '%' || #{keyword} || '%') </if>" +
            "</script>")
    long countPage(@Param("category") String category, @Param("keyword") String keyword);
}
