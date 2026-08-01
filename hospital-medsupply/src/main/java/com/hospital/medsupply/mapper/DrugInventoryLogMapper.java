package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugInventoryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存流水表 Mapper
 */
@Mapper
public interface DrugInventoryLogMapper {

    /** 插入流水记录 */
    int insert(DrugInventoryLog log);

    /** 按药品 ID 分页查询流水 */
    List<DrugInventoryLog> selectByDrugId(@Param("drugId") Long drugId,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    /** 按药品 ID 统计流水条数 */
    long countByDrugId(@Param("drugId") Long drugId);
}
