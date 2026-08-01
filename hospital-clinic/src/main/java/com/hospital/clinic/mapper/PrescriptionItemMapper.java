package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.PrescriptionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 处方明细表 Mapper
 */
@Mapper
public interface PrescriptionItemMapper {

    /** 批量插入处方明细 */
    int insertBatch(@Param("items") List<PrescriptionItem> items);

    /** 根据处方 ID 查询明细列表 */
    List<PrescriptionItem> selectByPrescriptionId(@Param("prescriptionId") Long prescriptionId);
}
