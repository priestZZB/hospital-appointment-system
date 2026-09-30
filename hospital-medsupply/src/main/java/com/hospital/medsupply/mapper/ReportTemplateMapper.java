package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ReportTemplate;
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

/**
 * 报告模板字典 Mapper（注解式，D4）
 */
@Mapper
public interface ReportTemplateMapper {

    /** 插入模板（useGeneratedKeys 回填主键，Oracle 必须显式 keyColumn） */
    @Insert("INSERT INTO report_template (modality, body_part, template_type, content, status, create_time) " +
            "VALUES (#{modality}, #{bodyPart}, #{templateType}, #{content}, #{status}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ReportTemplate template);

    /** 更新模板（modality/bodyPart/templateType/content 全量覆盖） */
    @Update("UPDATE report_template SET modality = #{modality}, body_part = #{bodyPart}, " +
            "template_type = #{templateType}, content = #{content} WHERE id = #{id}")
    int update(ReportTemplate template);

    /** 根据主键查询 */
    @Select("SELECT * FROM report_template WHERE id = #{id}")
    @Results(id = "templateMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "modality", column = "modality"),
            @Result(property = "bodyPart", column = "body_part"),
            @Result(property = "templateType", column = "template_type"),
            @Result(property = "content", column = "content"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time")
    })
    ReportTemplate selectById(@Param("id") Long id);

    /**
     * 启用中的模板查询（医生开报告套模板用）
     * <p>
     * bodyPart 传值时返回「精确部位 + 通用（body_part IS NULL）」两类，
     * 排序保证精确部位在前（服务层按 template_type 各取第一条即实现精确优先）；
     * bodyPart 为空时仅返回通用模板。
     */
    @Select("<script>" +
            "SELECT * FROM report_template WHERE status = 1 AND modality = #{modality} " +
            "<choose>" +
            "<when test='bodyPart != null and bodyPart != \"\"'>" +
            " AND (body_part = #{bodyPart} OR body_part IS NULL) " +
            " ORDER BY CASE WHEN body_part IS NULL THEN 1 ELSE 0 END ASC, id ASC" +
            "</when>" +
            "<otherwise>" +
            " AND body_part IS NULL ORDER BY id ASC" +
            "</otherwise>" +
            "</choose>" +
            "</script>")
    List<ReportTemplate> selectActive(@Param("modality") String modality,
                                      @Param("bodyPart") String bodyPart);

    /** 分页查询（检查类别可选，含停用模板，新模板在前） */
    @Select("<script>" +
            "SELECT * FROM report_template WHERE 1=1 " +
            "<if test='modality != null and modality != \"\"'> AND modality = #{modality} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @ResultMap("templateMap")
    List<ReportTemplate> selectByPage(@Param("modality") String modality,
                                      @Param("offset") Integer offset,
                                      @Param("limit") Integer limit);

    /** 分页统计模板数量（条件与 selectByPage 一致） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM report_template WHERE 1=1 " +
            "<if test='modality != null and modality != \"\"'> AND modality = #{modality} </if>" +
            "</script>")
    long countPage(@Param("modality") String modality);

    /** 删除模板（逻辑删除：status 置 0 停用） */
    @Update("UPDATE report_template SET status = 0 WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
