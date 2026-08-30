package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 处方主表 Mapper
 */
@Mapper
public interface PrescriptionMapper {

    /** 插入处方 */
    int insert(Prescription prescription);

    /** 根据主键查询 */
    Prescription selectById(@Param("id") Long id);

    /** 根据处方编号查询 */
    Prescription selectByNo(@Param("prescriptionNo") String prescriptionNo);

    /** 根据病历 ID 查询处方列表 */
    List<Prescription> selectByMedicalRecordId(@Param("medicalRecordId") Long medicalRecordId);

    /** 按患者 ID 分页查询 */
    List<Prescription> selectByPatientId(@Param("patientId") Long patientId,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);

    /** 按患者 ID 统计总数 */
    long countByPatientId(@Param("patientId") Long patientId);

    /** 更新处方状态 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reviewComment") String reviewComment,
                     @Param("expectedStatus") String expectedStatus);

    /** 缴费回写：更新缴费状态与实收金额 */
    int markPaid(@Param("id") Long id,
                 @Param("amount") BigDecimal amount,
                 @Param("payStatus") String payStatus);

    /** 查询患者未缴费处方列表（按创建时间倒序） */
    List<Prescription> selectUnpaidByPatient(@Param("patientId") Long patientId);

    /** 退费回写：缴费状态置为 REFUNDED（不动实收金额） */
    int markRefunded(@Param("id") Long id);
}
