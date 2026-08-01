package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 药品库存表 Mapper
 */
@Mapper
public interface DrugInventoryMapper {

    /** 根据药品 ID 查询库存 */
    DrugInventory selectByDrugId(@Param("drugId") Long drugId);

    /** 根据主键查询 */
    DrugInventory selectById(@Param("id") Long id);

    /** 插入库存记录 */
    int insert(DrugInventory inventory);

    /**
     * 乐观锁扣减库存
     * <p>
     * SQL: UPDATE drug_inventory SET current_stock = current_stock - #{quantity},
     * version = version + 1 WHERE drug_id = #{drugId}
     * AND current_stock >= #{quantity} AND version = #{version}
     *
     * @return 受影响行数，0 表示库存不足或版本冲突
     */
    int deductStock(@Param("drugId") Long drugId,
                    @Param("quantity") Integer quantity,
                    @Param("version") Integer version);

    /**
     * 入库操作
     *
     * @return 受影响行数
     */
    int addStock(@Param("drugId") Long drugId,
                 @Param("quantity") Integer quantity,
                 @Param("version") Integer version);

    /** 更新库存阈值 */
    int updateThreshold(@Param("drugId") Long drugId,
                        @Param("minThreshold") Integer minThreshold);

    /** 查询低库存列表（current_stock <= min_threshold） */
    List<DrugInventory> selectLowStock();
}
