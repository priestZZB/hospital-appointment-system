package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.vo.AppointmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预约订单表 Mapper
 */
@Mapper
public interface AppointmentMapper {

    /** 插入 */
    int insert(Appointment appointment);

    /** 根据主键查询 */
    Appointment selectById(@Param("id") Long id);

    /** 根据预约编号查询 */
    Appointment selectByAppointmentNo(@Param("appointmentNo") String appointmentNo);

    /** 根据号源查询 */
    Appointment selectBySlotId(@Param("slotId") Long slotId);

    /** 根据患者查询预约列表 */
    List<Appointment> selectByPatientId(@Param("patientId") Long patientId);

    /** 根据患者查询预约列表（联表：含医生/科室/号源详情） */
    List<AppointmentVO> selectByPatientIdWithDetail(@Param("patientId") Long patientId);

    /** 根据主键查询详情（联表：含医生/科室/号源） */
    AppointmentVO selectByIdWithDetail(@Param("id") Long id);

    /** 更新订单状态（带预期当前状态，防竞态覆盖） */
    int updateOrderStatus(@Param("id") Long id,
                          @Param("orderStatus") String orderStatus,
                          @Param("expectedStatus") String expectedStatus);

    /** 取消预约（含原因） */
    int cancel(@Param("id") Long id,
               @Param("orderStatus") String orderStatus,
               @Param("cancelReason") String cancelReason);
}
