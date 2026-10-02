package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.IcdDict;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * ICD-10 诊断字典 Mapper（迭代9 J3，注解式）
 * <p>
 * 供病历提交时校验 diagnosisCode 是否在字典内（{@code countEnabledByCode}），
 * 以及管理端字典 CRUD / 前端录入联想分页查询。
 */
@Mapper
public interface IcdDictMapper {

    /** 插入字典条目 */
    @Insert("INSERT INTO icd_dict (icd_code, icd_name, category, is_common, status, create_time) "
            + "VALUES (#{icdCode}, #{icdName}, #{category}, COALESCE(#{isCommon}, 0), COALESCE(#{status}, 1), SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(IcdDict dict);

    /** 根据主键查询 */
    @Select("SELECT id, icd_code, icd_name, category, is_common, status, create_time "
            + "FROM icd_dict WHERE id = #{id}")
    IcdDict selectById(@Param("id") Long id);

    /** 根据编码查询（含停用条目，供查重） */
    @Select("SELECT id, icd_code, icd_name, category, is_common, status, create_time "
            + "FROM icd_dict WHERE icd_code = #{icdCode}")
    IcdDict selectByCode(@Param("icdCode") String icdCode);

    /**
     * 分页查询：keyword 模糊匹配 code/name，category 精确筛选，常用条目优先
     * （MyBatis application.yml 已开启 map-underscore-to-camel-case，无需 @Results）
     */
    @Select("<script>"
            + "SELECT id, icd_code, icd_name, category, is_common, status, create_time "
            + "FROM icd_dict "
            + "WHERE 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  AND (icd_code LIKE '%' || #{keyword} || '%' OR icd_name LIKE '%' || #{keyword} || '%') "
            + "</if>"
            + "<if test=\"category != null and category != ''\">"
            + "  AND category = #{category} "
            + "</if>"
            + "ORDER BY is_common DESC, icd_code ASC "
            + "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY"
            + "</script>")
    List<IcdDict> selectPage(@Param("keyword") String keyword,
                             @Param("category") String category,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /** 分页总数（条件同 selectPage） */
    @Select("<script>"
            + "SELECT COUNT(*) FROM icd_dict WHERE 1 = 1 "
            + "<if test=\"keyword != null and keyword != ''\">"
            + "  AND (icd_code LIKE '%' || #{keyword} || '%' OR icd_name LIKE '%' || #{keyword} || '%') "
            + "</if>"
            + "<if test=\"category != null and category != ''\">"
            + "  AND category = #{category} "
            + "</if>"
            + "</script>")
    long countPage(@Param("keyword") String keyword, @Param("category") String category);

    /** 更新字典条目 */
    @Update("UPDATE icd_dict SET icd_code = #{icdCode}, icd_name = #{icdName}, category = #{category}, "
            + "is_common = COALESCE(#{isCommon}, 0), status = COALESCE(#{status}, 1) "
            + "WHERE id = #{id}")
    int update(IcdDict dict);

    /** 删除字典条目（medical_record.diagnosis_code 为自由文本引用，无外键，物理删除安全） */
    @Delete("DELETE FROM icd_dict WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 校验某编码是否存在于启用的字典中
     * （病历提交校验 J3：diagnosisCode 非空时必须命中本查询）
     */
    @Select("SELECT COUNT(1) FROM icd_dict WHERE status = 1 AND (icd_code = #{icdCode} OR INSTR(#{icdCode}, icd_code || '.') = 1 OR INSTR(icd_code, #{icdCode} || '.') = 1)")
    long countEnabledByCode(@Param("icdCode") String icdCode);

    /** 章节分类列表（前端筛选下拉用） */
    @Select("SELECT DISTINCT category FROM icd_dict WHERE category IS NOT NULL ORDER BY category")
    List<String> selectCategories();
}
