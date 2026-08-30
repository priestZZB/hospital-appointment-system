package com.hospital.auth.mapper;

import com.hospital.auth.entity.Position;
import com.hospital.auth.dto.PositionPageQueryDTO;
import com.hospital.auth.vo.PositionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 岗位表 Mapper（迭代 5 阶段 2）
 */
@Mapper
public interface PositionMapper {

    /**
     * 分页查询岗位（keyword 模糊匹配岗位名称/编码，status 过滤）
     */
    List<PositionVO> selectPage(PositionPageQueryDTO dto);

    /**
     * 分页查询总数
     */
    long countPage(PositionPageQueryDTO dto);

    /**
     * 按主键查询
     */
    Position selectById(@Param("id") Long id);

    /**
     * 按编码查询（唯一性校验用）
     */
    Position selectByCode(@Param("positionCode") String positionCode);

    /**
     * 查询全部启用岗位（分配岗位下拉用）
     */
    List<Position> selectEnabledAll();

    /**
     * 插入岗位，自动回填主键
     */
    int insert(Position position);

    /**
     * 更新岗位
     */
    int update(Position position);

    /**
     * 删除岗位（物理删除；被用户引用时由 Service 层拒绝）
     */
    int deleteById(@Param("id") Long id);

    /**
     * 统计引用该岗位的用户数（删除前校验）
     */
    long countUserByPositionId(@Param("positionId") Long positionId);
}