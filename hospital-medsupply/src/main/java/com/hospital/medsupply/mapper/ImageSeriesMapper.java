package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ImageSeries;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 影像序列表 Mapper（注解式，D2）
 */
@Mapper
public interface ImageSeriesMapper {

    /** 插入影像序列（object_keys 为 MinIO 对象 key 的 JSON 数组，CLOB 直存字符串） */
    @Insert("INSERT INTO image_series (series_no, application_id, patient_id, modality, description, " +
            "image_count, object_keys, upload_by, create_time) " +
            "VALUES (#{seriesNo}, #{applicationId}, #{patientId}, #{modality}, #{description}, " +
            "#{imageCount}, #{objectKeys}, #{uploadBy}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ImageSeries series);

    /** 根据主键查询 */
    @Select("SELECT * FROM image_series WHERE id = #{id}")
    @Results(id = "seriesMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "seriesNo", column = "series_no"),
            @Result(property = "applicationId", column = "application_id"),
            @Result(property = "patientId", column = "patient_id"),
            @Result(property = "modality", column = "modality"),
            @Result(property = "description", column = "description"),
            @Result(property = "imageCount", column = "image_count"),
            @Result(property = "objectKeys", column = "object_keys"),
            @Result(property = "uploadBy", column = "upload_by"),
            @Result(property = "createTime", column = "create_time")
    })
    ImageSeries selectById(@Param("id") Long id);

    /** 按检查申请查询影像序列（新序列在前） */
    @Select("SELECT * FROM image_series WHERE application_id = #{applicationId} " +
            "ORDER BY create_time DESC, id DESC")
    @ResultMap("seriesMap")
    List<ImageSeries> selectByApplication(@Param("applicationId") Long applicationId);

    /** 分页查询（检查类别可选，新序列在前） */
    @Select("<script>" +
            "SELECT * FROM image_series WHERE 1=1 " +
            "<if test='modality != null and modality != \"\"'> AND modality = #{modality} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @ResultMap("seriesMap")
    List<ImageSeries> selectByPage(@Param("modality") String modality,
                                   @Param("offset") Integer offset,
                                   @Param("limit") Integer limit);

    /** 分页统计影像序列数量（条件与 selectByPage 一致） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM image_series WHERE 1=1 " +
            "<if test='modality != null and modality != \"\"'> AND modality = #{modality} </if>" +
            "</script>")
    long countPage(@Param("modality") String modality);

    /** 删除影像序列（物理删除元数据；MinIO 对象由存储生命周期策略清理，毕设简化不做对象回删） */
    @Delete("DELETE FROM image_series WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
