package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.Slot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 号源表 Mapper
 */
@Mapper
public interface SlotMapper {

    /** 批量插入号源 */
    int batchInsert(@Param("list") List<Slot> slots);

    /** 根据排班查询可用号源 */
    List<Slot> selectAvailableBySchedule(@Param("scheduleId") Long scheduleId);

    /** 根据主键查询 */
    Slot selectById(@Param("id") Long id);

    /** 根据科室+日期查询号源（联表查医生/科室信息） */
    List<Slot> selectAvailableByDeptAndDate(@Param("departmentId") Long departmentId,
                                            @Param("scheduleDate") LocalDate scheduleDate);

    /** 乐观锁扣减号源 */
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("status") String status,
                                @Param("version") Integer version);

    /** 统计某排班的可用号源数 */
    int countAvailableBySchedule(@Param("scheduleId") Long scheduleId);

    /** 释放号源 */
    int releaseSlot(@Param("id") Long id, @Param("version") Integer version);

    /** 批量取消号源（排班级联取消） */
    int updateStatusByScheduleId(@Param("scheduleId") Long scheduleId, @Param("status") String status);
}
