package com.hospital.auth.mapper;

import com.hospital.auth.dto.DataScopeApplyQueryDTO;
import com.hospital.auth.entity.DataScopeApply;
import com.hospital.auth.vo.DataScopeApplyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 跨科室数据范围申请表 Mapper（迭代 5 阶段 3）
 */
@Mapper
public interface DataScopeApplyMapper {

    /**
     * 分页查询申请（本人/待审批通用）
     */
    List<DataScopeApplyVO> selectPage(DataScopeApplyQueryDTO dto);

    /**
     * 分页查询总数
     */
    long countPage(DataScopeApplyQueryDTO dto);

    /**
     * 按主键查询
     */
    DataScopeApply selectById(@Param("id") Long id);

    /**
     * 插入申请
     */
    int insert(DataScopeApply apply);

    /**
     * 审批更新（状态/审批人/审批时间/意见/过期时间）
     */
    int updateApprove(DataScopeApply apply);

    /**
     * 查询用户对某科室的有效授权（PENDING/APPROVED 且未过期）
     */
    List<DataScopeApply> selectActiveByUserAndDept(@Param("userId") Long userId,
                                                   @Param("departmentId") Long departmentId);
}