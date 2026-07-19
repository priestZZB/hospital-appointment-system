package com.hospital.patient.mapper;

import com.hospital.patient.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 患者档案表 Mapper
 */
@Mapper
public interface PatientMapper {

    /** 根据 userId 查询患者档案 */
    Patient selectByUserId(@Param("userId") Long userId);

    /** 根据主键查询 */
    Patient selectById(@Param("id") Long id);

    /** 插入患者档案，自动回填主键 */
    int insert(Patient patient);

    /** 更新患者档案 */
    int update(Patient patient);
}
