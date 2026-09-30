package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugRule;
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
 * CDSS 合理用药规则 Mapper（注解式）
 */
@Mapper
public interface DrugRuleMapper {

    /** 插入规则（默认启用） */
    @Insert("INSERT INTO drug_rule (rule_type, drug_id, paired_drug_id, max_single_dose, max_daily_dose, " +
            "severity, description, status, create_time) " +
            "VALUES (#{ruleType}, #{drugId}, #{pairedDrugId}, #{maxSingleDose}, #{maxDailyDose}, " +
            "#{severity}, #{description}, 1, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DrugRule rule);

    /** 更新规则 */
    @Update("UPDATE drug_rule SET rule_type = #{ruleType}, drug_id = #{drugId}, " +
            "paired_drug_id = #{pairedDrugId}, max_single_dose = #{maxSingleDose}, " +
            "max_daily_dose = #{maxDailyDose}, severity = #{severity}, description = #{description} " +
            "WHERE id = #{id}")
    int update(DrugRule rule);

    /** 删除规则（软删 status=0） */
    @Update("UPDATE drug_rule SET status = 0 WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 查询全部启用规则（支持类型/药品筛选） */
    @Select("<script>" +
            "SELECT * FROM drug_rule WHERE status = 1 " +
            "<if test='ruleType != null and ruleType != \"\"'> AND rule_type = #{ruleType} </if>" +
            "<if test='drugId != null'> AND (drug_id = #{drugId} OR paired_drug_id = #{drugId}) </if>" +
            "ORDER BY id DESC" +
            "</script>")
    @Results(id = "ruleMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "ruleType", column = "rule_type"),
            @Result(property = "drugId", column = "drug_id"),
            @Result(property = "pairedDrugId", column = "paired_drug_id"),
            @Result(property = "maxSingleDose", column = "max_single_dose"),
            @Result(property = "maxDailyDose", column = "max_daily_dose"),
            @Result(property = "severity", column = "severity"),
            @Result(property = "description", column = "description"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time")
    })
    List<DrugRule> selectActive(@Param("ruleType") String ruleType, @Param("drugId") Long drugId);

    /** 按药品查询启用规则（含 paired 配对方向） */
    @Select("SELECT * FROM drug_rule WHERE status = 1 AND (drug_id = #{drugId} OR paired_drug_id = #{drugId}) " +
            "ORDER BY id DESC")
    @ResultMap("ruleMap")
    List<DrugRule> selectByDrugId(@Param("drugId") Long drugId);

    /** 根据主键查询 */
    @Select("SELECT * FROM drug_rule WHERE id = #{id}")
    @ResultMap("ruleMap")
    DrugRule selectById(@Param("id") Long id);
}
