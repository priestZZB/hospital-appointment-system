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

    /** 仅取消仍可用的号源（保留 BOOKED，由取消预约流程释放） */
    int updateAvailableStatusByScheduleId(@Param("scheduleId") Long scheduleId, @Param("status") String status);

    // ==================== 迭代9 门诊流程补强 ====================

    /** 设置单个号源的通道类型（NORMAL/GREEN，绿色通道管理 A4） */
    int updateChannelType(@Param("id") Long id, @Param("channelType") String channelType);

    /**
     * 批量将某排班下按号序最靠前的 N 个可用号源设为目标通道类型
     * （绿色通道批量划绿 A4；只处理 AVAILABLE 且通道不同的号源）
     */
    int updateChannelTypeFirstN(@Param("scheduleId") Long scheduleId,
                                @Param("channelType") String channelType,
                                @Param("count") int count);
}
