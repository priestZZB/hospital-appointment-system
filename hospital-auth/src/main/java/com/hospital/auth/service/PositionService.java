package com.hospital.auth.service;

import com.hospital.auth.dto.AssignPositionDTO;
import com.hospital.auth.dto.PositionPageQueryDTO;
import com.hospital.auth.dto.PositionSaveDTO;
import com.hospital.auth.entity.Position;
import com.hospital.auth.mapper.PositionMapper;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.auth.vo.PositionVO;
import com.hospital.common.constant.RoleConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 岗位管理服务（迭代 5 阶段 2）
 * <p>
 * 岗位 = 部门 + 职务，纯展示、不参与权限。
 * 权限约束：
 * <ul>
 *   <li>超管：岗位全部权限（增删改查 + 分配人员）；</li>
 *   <li>管理员：可查看岗位、给非管理员分配岗位；不可增删改岗位、不可动超管/其他管理员；</li>
 *   <li>分配/撤销岗位时校验目标用户角色（超管/管理员仅超管可分配，管理员不可动超管）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;
    private final UserMapper userMapper;

    /**
     * 分页查询岗位（含在岗人数）
     */
    public UserService.PageResult<PositionVO> pageQuery(PositionPageQueryDTO dto) {
        List<PositionVO> list = positionMapper.selectPage(dto);
        long total = positionMapper.countPage(dto);
        return new UserService.PageResult<>(list, total, dto.getPageNo(), dto.getPageSize());
    }

    /**
     * 查询全部启用岗位（分配岗位下拉用）
     */
    public List<Position> listEnabled() {
        return positionMapper.selectEnabledAll();
    }

    /**
     * 创建岗位（仅超管）
     */
    @Transactional(rollbackFor = Exception.class)
    public PositionVO create(PositionSaveDTO dto) {
        if (positionMapper.selectByCode(dto.getPositionCode()) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位编码已存在: " + dto.getPositionCode());
        }
        Position p = new Position();
        p.setPositionCode(dto.getPositionCode());
        p.setPositionName(dto.getPositionName());
        p.setDepartmentId(dto.getDepartmentId());
        p.setDepartmentName(dto.getDepartmentName());
        p.setTitle(dto.getTitle());
        p.setDescription(dto.getDescription());
        p.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        p.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        positionMapper.insert(p);
        log.info("[岗位] 创建岗位: id={}, code={}", p.getId(), p.getPositionCode());
        return toVO(p, 0L);
    }

    /**
     * 更新岗位（仅超管）
     */
    @Transactional(rollbackFor = Exception.class)
    public PositionVO update(Long id, PositionSaveDTO dto) {
        Position existing = positionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位不存在: id=" + id);
        }
        Position byCode = positionMapper.selectByCode(dto.getPositionCode());
        if (byCode != null && !byCode.getId().equals(id)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位编码已被其他岗位占用");
        }
        existing.setPositionCode(dto.getPositionCode());
        existing.setPositionName(dto.getPositionName());
        existing.setDepartmentId(dto.getDepartmentId());
        existing.setDepartmentName(dto.getDepartmentName());
        existing.setTitle(dto.getTitle());
        existing.setDescription(dto.getDescription());
        existing.setStatus(dto.getStatus() != null ? dto.getStatus() : existing.getStatus());
        existing.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : existing.getSortOrder());
        positionMapper.update(existing);
        log.info("[岗位] 更新岗位: id={}", id);
        return toVO(existing, positionMapper.countUserByPositionId(id));
    }

    /**
     * 删除岗位（仅超管；被用户引用时拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Position existing = positionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位不存在: id=" + id);
        }
        long userCount = positionMapper.countUserByPositionId(id);
        if (userCount > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该岗位下仍有 " + userCount + " 名在岗人员，请先解除分配再删除");
        }
        positionMapper.deleteById(id);
        log.info("[岗位] 删除岗位: id={}", id);
    }

    /**
     * 给用户分配/撤销岗位（超管不限；管理员仅可给非管理员分配）
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(AssignPositionDTO dto) {
        if (userMapper.selectById(dto.getUserId()) == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (dto.getPositionId() != null && positionMapper.selectById(dto.getPositionId()) == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位不存在: id=" + dto.getPositionId());
        }

        // 管理员分配岗位约束：不可动超管/其他管理员（仅超管可分配超管/管理员岗位）
        List<String> targetRoles = userMapper.selectRoleCodesByUserId(dto.getUserId());
        boolean targetIsSuperAdmin = targetRoles.contains(RoleConstant.SUPER_ADMIN);
        boolean targetIsAdmin = targetRoles.contains(RoleConstant.ADMIN);
        if (!UserContext.isSuperAdmin() && (targetIsSuperAdmin || targetIsAdmin)) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅超级管理员可为超管/管理员账号分配岗位");
        }
        // 管理员不能操作自己的岗位（避免自我调整脱离管控）
        if (!UserContext.isSuperAdmin() && UserContext.getUserId() != null
                && UserContext.getUserId().equals(dto.getUserId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "不能操作自己的岗位");
        }

        userMapper.updatePositionId(dto.getUserId(), dto.getPositionId());
        log.info("[岗位] 分配岗位: userId={}, positionId={}", dto.getUserId(), dto.getPositionId());
    }

    /**
     * 岗位详情
     */
    public PositionVO detail(Long id) {
        Position p = positionMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位不存在: id=" + id);
        }
        return toVO(p, positionMapper.countUserByPositionId(id));
    }

    private PositionVO toVO(Position p, Long userCount) {
        PositionVO vo = new PositionVO();
        vo.setId(p.getId());
        vo.setPositionCode(p.getPositionCode());
        vo.setPositionName(p.getPositionName());
        vo.setDepartmentId(p.getDepartmentId());
        vo.setDepartmentName(p.getDepartmentName());
        vo.setTitle(p.getTitle());
        vo.setDescription(p.getDescription());
        vo.setStatus(p.getStatus());
        vo.setSortOrder(p.getSortOrder());
        vo.setUserCount(userCount);
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }
}