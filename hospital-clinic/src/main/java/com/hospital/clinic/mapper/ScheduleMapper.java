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

    /** 更新排班审批状态 */
    int updateAuditStatus(@Param("id") Long id, @Param("auditStatus") String auditStatus);

    /** 按审批状态查询排班（用于门诊部确认列表） */
    List<Schedule> selectByAuditStatus(@Param("auditStatus") String auditStatus,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    // ==================== 迭代9 门诊流程补强 ====================

    /** 设置排班加号开关（0-禁止 / 1-允许加号超挂，迭代9 A2） */
    int updateOverbook(@Param("id") Long id, @Param("overbook") Integer overbook);

    /** 设置排班号别（NORMAL-普通 / EXPERT-专家号，迭代9 A5） */
    int updateFeeType(@Param("id") Long id, @Param("feeType") String feeType);
}
