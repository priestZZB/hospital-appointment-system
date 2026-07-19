package com.hospital.patient.mapper;

import com.hospital.patient.entity.VisitCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 就诊卡表 Mapper
 */
@Mapper
public interface VisitCardMapper {

    /** 根据患者 ID 查询就诊卡列表 */
    List<VisitCard> selectByPatientId(@Param("patientId") Long patientId);

    /** 插入就诊卡，自动回填主键 */
    int insert(VisitCard visitCard);
}
