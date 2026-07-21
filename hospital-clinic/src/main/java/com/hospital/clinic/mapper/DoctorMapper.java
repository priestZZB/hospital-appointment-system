package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 医生表 Mapper
 */
@Mapper
public interface DoctorMapper {

    /** 分页查询 */
    List<Doctor> selectPage(@Param("departmentId") Long departmentId,
                            @Param("keyword") String keyword,
                            @Param("offset") Integer offset,
                            @Param("pageSize") Integer pageSize);

    /** 分页总数 */
    long countPage(@Param("departmentId") Long departmentId,
                   @Param("keyword") String keyword);

    /** 根据主键查询 */
    Doctor selectById(@Param("id") Long id);

    /** 根据 userId 查询 */
    Doctor selectByUserId(@Param("userId") Long userId);

    /** 插入 */
    int insert(Doctor doctor);

    /** 更新 */
    int update(Doctor doctor);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
