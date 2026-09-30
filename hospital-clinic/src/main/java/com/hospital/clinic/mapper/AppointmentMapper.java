package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.vo.AppointmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    /** 管理端分页查询预约（联表：医生/科室/号源，多条件筛选） */
    List<AppointmentVO> selectPageWithDetail(@Param("departmentId") Long departmentId,
                                             @Param("doctorId") Long doctorId,
                                             @Param("patientId") Long patientId,
                                             @Param("orderStatus") String orderStatus,
                                             @Param("appointmentDate") LocalDate appointmentDate,
                                             @Param("offset") Integer offset,
                                             @Param("pageSize") Integer pageSize);

    /** 管理端分页查询预约总数 */
    long countPageWithDetail(@Param("departmentId") Long departmentId,
                             @Param("doctorId") Long doctorId,
                             @Param("patientId") Long patientId,
                             @Param("orderStatus") String orderStatus,
                             @Param("appointmentDate") LocalDate appointmentDate);

    /** 更新订单状态（带预期当前状态，防竞态覆盖） */
    int updateOrderStatus(@Param("id") Long id,
                          @Param("orderStatus") String orderStatus,
                          @Param("expectedStatus") String expectedStatus);

    /** 取消预约（含原因，带预期状态防竞态覆盖） */
    int cancel(@Param("id") Long id,
               @Param("orderStatus") String orderStatus,
               @Param("cancelReason") String cancelReason,
               @Param("expectedStatus") String expectedStatus);

    /** 排班级联取消：将某排班下所有待支付预约置为已取消 */
    int cancelPendingPayByScheduleId(@Param("scheduleId") Long scheduleId,
                                     @Param("cancelReason") String cancelReason);

    /** 根据排班 ID 查询所有预约 */
    List<Appointment> selectByScheduleId(@Param("scheduleId") Long scheduleId);

    /** 更新 visit_status（带预期状态检查） */
    int updateVisitStatus(@Param("id") Long id,
                          @Param("visitStatus") String visitStatus,
                          @Param("expectedStatus") String expectedStatus);

    // ==================== BI 统计 ====================

    /** 按日期统计预约数 */
    Long countByDate(@Param("date") LocalDate date);

    /** 按日期统计完成就诊数 */
    Long countCompletedByDate(@Param("date") LocalDate date);

    /** 按日期统计收入（PAID） */
    java.math.BigDecimal sumRevenueByDate(@Param("date") LocalDate date);

    /** 按科室分组统计当日预约数 */
    List<java.util.Map<String, Object>> countGroupByDept(@Param("date") LocalDate date);

    /** 统计某患者从指定日期起（含）已完成就诊次数（用于复诊识别） */
    long countCompletedSince(@Param("patientId") Long patientId,
                             @Param("date") LocalDate date);

    // ==================== 迭代9 门诊流程补强 ====================

    /**
     * 退号改期：更新预约的号源与排班引用（含冗余的日期/时段/号序，迭代9 A3）。
     * register_fee 与支付订单保持不变（不重复收费），由服务层校验号源状态。
     */
    int updateReschedule(@Param("id") Long id,
                         @Param("newSlotId") Long newSlotId,
                         @Param("newScheduleId") Long newScheduleId,
                         @Param("appointmentDate") LocalDate appointmentDate,
                         @Param("period") String period,
                         @Param("slotSeq") Integer slotSeq);
}
