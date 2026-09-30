package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.Drug;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 药品目录表 Mapper
 */
@Mapper
public interface DrugMapper {

    /** 分页查询（支持药品名称模糊搜索） */
    List<Drug> selectPage(@Param("keyword") String keyword,
                          @Param("offset") Integer offset,
                          @Param("limit") Integer limit);

    /** 统计总数 */
    long count(@Param("keyword") String keyword);

    /** 根据主键查询 */
    Drug selectById(@Param("id") Long id);

    /** 根据药品编码查询 */
    Drug selectByCode(@Param("drugCode") String drugCode);

    /** 按药品分类查询（启用状态，V8 B1 三分类） */
    List<Drug> selectByDrugType(@Param("drugType") String drugType);

    /** 更新药品三分类/管控级别/抗菌分级（V8 B1/B8/B10） */
    int updateDrugType(@Param("id") Long id,
                       @Param("drugType") String drugType,
                       @Param("controlLevel") String controlLevel,
                       @Param("antibioticLevel") String antibioticLevel);

    /** 插入药品，自动回填主键 */
    int insert(Drug drug);

    /** 更新药品 */
    int update(Drug drug);

    /** 更新药品状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
