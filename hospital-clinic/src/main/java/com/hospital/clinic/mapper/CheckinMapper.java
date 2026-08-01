package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Checkin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 签到记录表 Mapper
 */
@Mapper
public interface CheckinMapper {

    /** 插入签到记录 */
    int insert(Checkin checkin);

    /** 根据预约 ID 查询 */
    Checkin selectByAppointmentId(@Param("appointmentId") Long appointmentId);

    /** 根据主键查询 */
    Checkin selectById(@Param("id") Long id);

    /** 根据患者 ID 查询 */
    Checkin selectByPatientId(@Param("patientId") Long patientId);

    /** 按科室查询等待中的签到列表（按签到时间排序） */
    List<Checkin> selectWaitingByDeptId(@Param("departmentId") Long departmentId);

    /** 更新队列状态（带预期状态检查） */
    int updateQueueStatus(@Param("id") Long id,
                          @Param("queueStatus") String queueStatus,
                          @Param("expectedStatus") String expectedStatus);

    /** 更新叫号信息 */
    int updateCallInfo(@Param("id") Long id,
                       @Param("queueStatus") String queueStatus,
                       @Param("callCount") Integer callCount,
                       @Param("consultRoom") String consultRoom);

    /** 更新排队时间（过号重排） */
    int updateRejoin(@Param("id") Long id,
                     @Param("queueStatus") String queueStatus);
}
