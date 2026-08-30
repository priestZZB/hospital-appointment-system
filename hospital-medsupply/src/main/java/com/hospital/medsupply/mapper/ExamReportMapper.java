package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ExamReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 检查报告表 Mapper
 */
@Mapper
public interface ExamReportMapper {

    /** 插入报告 */
    int insert(ExamReport report);

    /** 根据申请 ID 查询 */
    ExamReport selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 根据主键查询 */
    ExamReport selectById(@Param("id") Long id);

    /** 按患者 ID 分页查询报告列表 */
    List<ExamReport> selectByPatientId(@Param("patientId") Long patientId,
                                       @Param("offset") Integer offset,
                                       @Param("limit") Integer limit);

    /** 按患者 ID 统计总数 */
    long countByPatientId(@Param("patientId") Long patientId);

    /** 更新报告状态 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("completeTime") java.time.LocalDateTime completeTime);

    /** 报告审核（发布/驳回），写审核人、审核时间、审核意见 */
    int audit(@Param("id") Long id,
              @Param("status") String status,
              @Param("auditorId") Long auditorId,
              @Param("auditComment") String auditComment);
}
