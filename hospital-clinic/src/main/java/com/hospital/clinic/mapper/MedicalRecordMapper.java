package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.MedicalRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 病历表 Mapper
 */
@Mapper
public interface MedicalRecordMapper {

    /** 插入病历 */
    int insert(MedicalRecord record);

    /** 根据主键查询 */
    MedicalRecord selectById(@Param("id") Long id);

    /** 根据预约 ID 查询 */
    MedicalRecord selectByAppointmentId(@Param("appointmentId") Long appointmentId);

    /** 更新病历（草稿 → 提交） */
    int update(MedicalRecord record);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 按患者 ID 分页查询病历列表 */
    List<MedicalRecord> selectByPatientId(@Param("patientId") Long patientId,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    /** 按患者 ID 统计总数 */
    long countByPatientId(@Param("patientId") Long patientId);

    /** 按医生 ID 分页查询病历列表 */
    List<MedicalRecord> selectByDoctorId(@Param("doctorId") Long doctorId,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);
}
