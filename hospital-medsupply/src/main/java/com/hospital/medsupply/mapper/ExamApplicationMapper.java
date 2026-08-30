package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ExamApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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

    /** 执行登记：设置状态、执行时间、执行技师 */
    int markExecuted(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("execOperatorId") Long execOperatorId);

    /** 按状态查询申请列表 */
    List<ExamApplication> selectByStatus(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 检查缴费成功回写：写缴费状态与实收金额 */
    int markPaid(@Param("id") Long id,
                 @Param("amount") BigDecimal amount,
                 @Param("payStatus") String payStatus);

    /** 查询患者未缴费检查申请列表 */
    List<ExamApplication> selectUnpaidByPatient(@Param("patientId") Long patientId);

    /** 影像科（item_type != LAB）按状态查询申请列表 */
    List<ExamApplication> selectImagingByStatus(@Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /** 检验科（item_type = LAB）按状态查询申请列表 */
    List<ExamApplication> selectLabByStatus(@Param("status") String status,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /** 检查退费回写：缴费状态置为 REFUNDED（不动实收金额） */
    int markRefunded(@Param("id") Long id);
}
