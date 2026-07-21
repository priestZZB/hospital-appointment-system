package com.hospital.clinic.service;

import com.hospital.clinic.dto.DepartmentSaveDTO;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.vo.DepartmentVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 科室管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    /**
     * 科室列表（含关键词搜索）
     */
    public List<DepartmentVO> list(String keyword) {
        List<Department> list = departmentMapper.selectAll(keyword);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 科室详情
     */
    public DepartmentVO getById(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND);
        }
        return toVO(dept);
    }

    /**
     * 新增科室
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO create(DepartmentSaveDTO dto) {
        Department dept = new Department();
        dept.setDeptName(dto.getDeptName());
        dept.setDeptCode(dto.getDeptCode());
        dept.setDescription(dto.getDescription());
        dept.setLocation(dto.getLocation());
        dept.setPhone(dto.getPhone());
        dept.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dept.setStatus(1);
        departmentMapper.insert(dept);
        log.info("[科室] 新增成功: id={}, name={}", dept.getId(), dept.getDeptName());
        return toVO(dept);
    }

    /**
     * 编辑科室
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO update(Long id, DepartmentSaveDTO dto) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND);
        }
        dept.setDeptName(dto.getDeptName());
        dept.setDeptCode(dto.getDeptCode());
        dept.setDescription(dto.getDescription());
        dept.setLocation(dto.getLocation());
        dept.setPhone(dto.getPhone());
        dept.setSortOrder(dto.getSortOrder());
        departmentMapper.update(dept);
        log.info("[科室] 编辑成功: id={}", id);
        return toVO(departmentMapper.selectById(id));
    }

    /**
     * 更新科室状态（启用/停用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND);
        }
        departmentMapper.updateStatus(id, status);
        log.info("[科室] 状态变更: id={}, status={}", id, status);
    }

    // ==================== 实体 → VO 转换 ====================

    private DepartmentVO toVO(Department d) {
        return DepartmentVO.builder()
                .id(d.getId())
                .deptName(d.getDeptName())
                .deptCode(d.getDeptCode())
                .description(d.getDescription())
                .location(d.getLocation())
                .phone(d.getPhone())
                .status(d.getStatus())
                .sortOrder(d.getSortOrder())
                .createTime(d.getCreateTime())
                .build();
    }
}
