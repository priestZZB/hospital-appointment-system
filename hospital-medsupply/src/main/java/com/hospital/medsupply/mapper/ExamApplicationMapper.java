package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ExamApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 检查申请表 Mapper
 */
@Mapper
public interface ExamApplicationMapper {

    /** 插入申请 */
    int insert(ExamApplication application);

    /** 根据主键查询 */
    ExamApplication selectById(@Param("id") Long id);

    /** 根据申请编号查询 */
    ExamApplication selectByNo(@Param("applicationNo") String applicationNo);

    /** 按患者 ID 分页查询 */
    List<ExamApplication> selectByPatientId(@Param("patientId") Long patientId,
                                            @Param("offset") Integer offset,
                                            @Param("limit") Integer limit);

    /** 按患者 ID 统计总数 */
    long countByPatientId(@Param("patientId") Long patientId);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
