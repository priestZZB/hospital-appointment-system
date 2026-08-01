package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.StopApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 停诊申请表 Mapper
 */
@Mapper
public interface StopApplicationMapper {

    /** 插入停诊申请 */
    int insert(StopApplication application);

    /** 根据主键查询 */
    StopApplication selectById(@Param("id") Long id);

    /** 根据排班 ID 查询（同一排班只允许一个有效申请） */
    StopApplication selectByScheduleId(@Param("scheduleId") Long scheduleId);

    /** 按状态分页查询 */
    List<StopApplication> selectByStatus(@Param("status") String status,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);

    /** 按状态统计总数 */
    long countByStatus(@Param("status") String status);

    /** 按医生 ID 分页查询 */
    List<StopApplication> selectByDoctorId(@Param("doctorId") Long doctorId,
                                           @Param("offset") Integer offset,
                                           @Param("limit") Integer limit);

    /** 审批操作（通过/驳回） */
    int updateApproval(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("approveComment") String approveComment,
                       @Param("approvedBy") Long approvedBy,
                       @Param("affectedCount") Integer affectedCount,
                       @Param("refundTotal") java.math.BigDecimal refundTotal,
                       @Param("expectedStatus") String expectedStatus);
}
