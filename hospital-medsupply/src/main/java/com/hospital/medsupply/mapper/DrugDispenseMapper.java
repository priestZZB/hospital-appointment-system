package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugDispense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发药记录表 Mapper
 */
@Mapper
public interface DrugDispenseMapper {

    /** 插入发药记录 */
    int insert(DrugDispense dispense);

    /** 根据处方 ID 查询 */
    DrugDispense selectByPrescriptionId(@Param("prescriptionId") Long prescriptionId);

    /** 根据主键查询 */
    DrugDispense selectById(@Param("id") Long id);

    /** 按患者 ID 分页查询 */
    List<DrugDispense> selectByPatientId(@Param("patientId") Long patientId,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);

    /** 按患者 ID 统计总数 */
    long countByPatientId(@Param("patientId") Long patientId);

    /** 审核操作（通过或驳回） */
    int updateReview(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewOperatorId") Long reviewOperatorId);

    /** 发药确认 */
    int updateDispensed(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("dispenseOperatorId") Long dispenseOperatorId);
}
