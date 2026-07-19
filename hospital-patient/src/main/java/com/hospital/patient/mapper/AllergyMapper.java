package com.hospital.patient.mapper;

import com.hospital.patient.entity.Allergy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 过敏史表 Mapper
 */
@Mapper
public interface AllergyMapper {

    /** 根据患者 ID 查询过敏史列表 */
    List<Allergy> selectByPatientId(@Param("patientId") Long patientId);

    /** 插入过敏史，自动回填主键 */
    int insert(Allergy allergy);

    /** 删除过敏史（患者只能删除自己来源为 PATIENT 的记录） */
    int deleteById(@Param("id") Long id, @Param("patientId") Long patientId);
}
