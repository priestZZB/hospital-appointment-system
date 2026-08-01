package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ExamItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 检查检验项目表 Mapper
 */
@Mapper
public interface ExamItemMapper {

    /** 分页查询（支持名称/编码模糊搜索） */
    List<ExamItem> selectPage(@Param("keyword") String keyword,
                              @Param("itemType") String itemType,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    /** 统计总数 */
    long count(@Param("keyword") String keyword,
               @Param("itemType") String itemType);

    /** 根据主键查询 */
    ExamItem selectById(@Param("id") Long id);

    /** 根据项目编码查询 */
    ExamItem selectByCode(@Param("itemCode") String itemCode);

    /** 插入项目 */
    int insert(ExamItem examItem);

    /** 更新项目 */
    int update(ExamItem examItem);

    /** 更新项目状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
