package com.hospital.payment.mapper;

import com.hospital.payment.entity.InsuranceCatalog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 医保目录映射 Mapper（注解式，迭代11 H1）。
 * <p>
 * (item_type, item_ref_id) 由 uk_ins_cat 唯一约束兜底，服务层「先查后改/插」实现
 * UNIQUE 冲突转更新（upsert）语义。
 */
@Mapper
public interface InsuranceCatalogMapper {

    String COLS = "id, item_type, item_ref_id, item_name, catalog_class, reimburse_ratio, status, create_time, update_time";

    @Insert("INSERT INTO insurance_catalog (item_type, item_ref_id, item_name, catalog_class, reimburse_ratio, status, create_time, update_time) " +
            "VALUES (#{itemType}, #{itemRefId}, #{itemName}, #{catalogClass}, NVL(#{reimburseRatio}, 0), NVL(#{status}, 1), SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(InsuranceCatalog catalog);

    /** 更新映射内容（item_type/item_ref_id 为唯一键不随编辑变化） */
    @Update("UPDATE insurance_catalog SET item_name = #{itemName}, catalog_class = #{catalogClass}, " +
            "reimburse_ratio = NVL(#{reimburseRatio}, 0), status = NVL(#{status}, 1), update_time = SYSDATE WHERE id = #{id}")
    int update(InsuranceCatalog catalog);

    @Select("SELECT " + COLS + " FROM insurance_catalog WHERE id = #{id}")
    InsuranceCatalog selectById(@Param("id") Long id);

    /** 按类型 + 引用 ID 解析（结算与 resolve 共用；仅启用 status=1 的映射生效） */
    @Select("SELECT " + COLS + " FROM insurance_catalog " +
            "WHERE item_type = #{itemType} AND item_ref_id = #{refId} AND status = 1 FETCH FIRST 1 ROWS ONLY")
    InsuranceCatalog selectByTypeAndRef(@Param("itemType") String itemType, @Param("refId") Long refId);

    @Select("<script>SELECT " + COLS + " FROM insurance_catalog WHERE 1=1 " +
            "<if test='itemType != null and itemType != &quot;&quot;'> AND item_type = #{itemType} </if>" +
            "<if test='catalogClass != null and catalogClass != &quot;&quot;'> AND catalog_class = #{catalogClass} </if>" +
            " ORDER BY item_type, item_ref_id NULLS LAST, id" +
            " OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</script>")
    List<InsuranceCatalog> selectPage(@Param("itemType") String itemType, @Param("catalogClass") String catalogClass,
                                      @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM insurance_catalog WHERE 1=1 " +
            "<if test='itemType != null and itemType != &quot;&quot;'> AND item_type = #{itemType} </if>" +
            "<if test='catalogClass != null and catalogClass != &quot;&quot;'> AND catalog_class = #{catalogClass} </if>" +
            "</script>")
    long countPage(@Param("itemType") String itemType, @Param("catalogClass") String catalogClass);
}
