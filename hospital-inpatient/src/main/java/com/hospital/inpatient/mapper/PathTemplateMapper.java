package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.PathTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 临床路径模板 Mapper（注解式，迭代12 J1） */
@Mapper
public interface PathTemplateMapper {

    String COLS = "id, path_code, path_name, disease_name, standard_days, total_estimate, item_json, status, create_time";

    @Insert("INSERT INTO path_template (path_code, path_name, disease_name, standard_days, total_estimate, item_json, status, create_time) " +
            "VALUES (#{pathCode}, #{pathName}, #{diseaseName}, #{standardDays}, #{totalEstimate}, #{itemJson}, 1, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PathTemplate template);

    @Select("SELECT " + COLS + " FROM path_template WHERE id = #{id}")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "path_code", property = "pathCode"),
            @Result(column = "path_name", property = "pathName"),
            @Result(column = "disease_name", property = "diseaseName"),
            @Result(column = "standard_days", property = "standardDays"),
            @Result(column = "total_estimate", property = "totalEstimate"),
            @Result(column = "item_json", property = "itemJson"),
            @Result(column = "status", property = "status"),
            @Result(column = "create_time", property = "createTime")
    })
    PathTemplate selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM path_template " +
            "<where>" +
            "  <if test='keyword != null'> AND (path_code LIKE '%'||UPPER(#{keyword})||'%' OR path_name LIKE '%'||#{keyword}||'%' OR disease_name LIKE '%'||#{keyword}||'%')</if>" +
            "</where>" +
            " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "path_code", property = "pathCode"),
            @Result(column = "path_name", property = "pathName"),
            @Result(column = "disease_name", property = "diseaseName"),
            @Result(column = "standard_days", property = "standardDays"),
            @Result(column = "total_estimate", property = "totalEstimate"),
            @Result(column = "item_json", property = "itemJson"),
            @Result(column = "status", property = "status"),
            @Result(column = "create_time", property = "createTime")
    })
    List<PathTemplate> selectPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM path_template " +
            "<where>" +
            "  <if test='keyword != null'> AND (path_code LIKE '%'||UPPER(#{keyword})||'%' OR path_name LIKE '%'||#{keyword}||'%' OR disease_name LIKE '%'||#{keyword}||'%')</if>" +
            "</where></script>")
    long countPage(@Param("keyword") String keyword);

    @Update("UPDATE path_template SET path_name = #{pathName}, disease_name = #{diseaseName}, " +
            "standard_days = #{standardDays}, total_estimate = #{totalEstimate}, item_json = #{itemJson} " +
            "WHERE id = #{id}")
    int update(PathTemplate template);
}
