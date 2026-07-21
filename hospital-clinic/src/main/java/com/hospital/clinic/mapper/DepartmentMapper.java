package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 科室表 Mapper
 */
@Mapper
public interface DepartmentMapper {

    /** 查询全部启用科室 */
    List<Department> selectAll(@Param("keyword") String keyword);

    /** 根据主键查询 */
    Department selectById(@Param("id") Long id);

    /** 插入 */
    int insert(Department department);

    /** 更新 */
    int update(Department department);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
