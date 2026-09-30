package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.Specimen;
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
 * 标本采集表 Mapper（注解式）
 */
@Mapper
public interface SpecimenMapper {

    /** 插入标本记录（useGeneratedKeys 回填主键，Oracle 必须显式 keyColumn） */
    @Insert("INSERT INTO specimen (specimen_no, application_id, patient_id, specimen_type, " +
            "container, collect_site, status, collector_id, collect_time, create_time) " +
            "VALUES (#{specimenNo}, #{applicationId}, #{patientId}, #{specimenType}, " +
            "#{container}, #{collectSite}, #{status}, #{collectorId}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Specimen specimen);

    /** 根据主键查询 */
    @Select("SELECT * FROM specimen WHERE id = #{id}")
    @Results(id = "specimenMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "specimenNo", column = "specimen_no"),
            @Result(property = "applicationId", column = "application_id"),
            @Result(property = "patientId", column = "patient_id"),
            @Result(property = "specimenType", column = "specimen_type"),
            @Result(property = "container", column = "container"),
            @Result(property = "collectSite", column = "collect_site"),
            @Result(property = "status", column = "status"),
            @Result(property = "collectorId", column = "collector_id"),
            @Result(property = "collectTime", column = "collect_time"),
            @Result(property = "receiverId", column = "receiver_id"),
            @Result(property = "receiveTime", column = "receive_time"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "createTime", column = "create_time")
    })
    Specimen selectById(@Param("id") Long id);

    /** 按检验申请查询标本（一单可多管采血，最新在前） */
    @Select("SELECT * FROM specimen WHERE application_id = #{applicationId} " +
            "ORDER BY create_time DESC, id DESC")
    @ResultMap("specimenMap")
    List<Specimen> selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 按标本号查询（条码唯一） */
    @Select("SELECT * FROM specimen WHERE specimen_no = #{specimenNo}")
    @ResultMap("specimenMap")
    Specimen selectByNo(@Param("specimenNo") String specimenNo);

    /** 分页查询标本（支持状态/标本类型筛选，倒序） */
    @Select("<script>" +
            "SELECT * FROM specimen WHERE 1=1 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "<if test='specimenType != null and specimenType != \"\"'> AND specimen_type = #{specimenType} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @ResultMap("specimenMap")
    List<Specimen> selectByPage(@Param("status") String status,
                                @Param("specimenType") String specimenType,
                                @Param("offset") Integer offset,
                                @Param("limit") Integer limit);

    /** 分页统计标本数量 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM specimen WHERE 1=1 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "<if test='specimenType != null and specimenType != \"\"'> AND specimen_type = #{specimenType} </if>" +
            "</script>")
    long countPage(@Param("status") String status, @Param("specimenType") String specimenType);

    /** 核收/拒收：写状态 + 核收人 + 核收时间 */
    @Update("UPDATE specimen SET status = #{status}, receiver_id = #{receiverId}, receive_time = SYSDATE " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("receiverId") Long receiverId);

    /** 检测中流转（乐观条件：仅 RECEIVED 可进入 TESTING） */
    @Update("UPDATE specimen SET status = 'TESTING' WHERE id = #{id} AND status = 'RECEIVED'")
    int markTesting(@Param("id") Long id);

    /** 写备注（拒收原因等） */
    @Update("UPDATE specimen SET remark = #{remark} WHERE id = #{id}")
    int updateRemark(@Param("id") Long id, @Param("remark") String remark);

    /** 统计指定前缀（SP+yyyyMMdd）的标本号数量，供当日序列生成 */
    @Select("SELECT COUNT(*) FROM specimen WHERE specimen_no LIKE #{prefix} || '%'")
    long countByNoPrefix(@Param("prefix") String prefix);
}
