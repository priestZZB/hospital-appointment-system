package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班表 Mapper
 */
@Mapper
public interface ScheduleMapper {

    /** 插入 */
    int insert(Schedule schedule);

    /** 根据主键查询 */
    Schedule selectById(@Param("id") Long id);

    /** 按科室+日期范围查询排班 */
    List<Schedule> selectByDeptAndDateRange(@Param("departmentId") Long departmentId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    /** 根据主键批量查询 */
    List<Schedule> selectByIds(@Param("ids") List<Long> ids);

    /** 按医生+日期+时段查重 */
    Schedule selectByDoctorDatePeriod(@Param("doctorId") Long doctorId,
                                      @Param("scheduleDate") LocalDate scheduleDate,
                                      @Param("period") String period);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
