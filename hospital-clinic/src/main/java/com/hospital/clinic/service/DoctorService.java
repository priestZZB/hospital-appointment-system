package com.hospital.clinic.service;

import com.hospital.clinic.dto.DoctorSaveDTO;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.vo.DoctorVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 医生管理服务（列表/新增/编辑/状态）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;

    /**
     * 分页查询医生（可按科室/姓名关键字筛选）
     */
    public Map<String, Object> page(Long departmentId, String keyword, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<Doctor> doctors = doctorMapper.selectPage(departmentId, keyword, offset, pageSize);
        long total = doctorMapper.countPage(departmentId, keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("records", toVOList(doctors));
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 医生详情
     */
    public DoctorVO getById(Long id) {
        Doctor doctor = requireDoctor(id);
        return toVO(doctor);
    }

    /**
     * 新增医生
     */
    @Transactional(rollbackFor = Exception.class)
    public DoctorVO create(DoctorSaveDTO dto) {
        requireDepartment(dto.getDepartmentId());
        if (dto.getUserId() != null && doctorMapper.selectByUserId(dto.getUserId()) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该账号已关联医生档案");
        }
        Doctor doctor = new Doctor();
        applyDto(doctor, dto);
        doctor.setStatus(1);
        doctorMapper.insert(doctor);
        log.info("[医生] 新增医生: doctorId={}, name={}", doctor.getId(), doctor.getName());
        return toVO(doctor);
    }

    /**
     * 编辑医生
     */
    @Transactional(rollbackFor = Exception.class)
    public DoctorVO update(Long id, DoctorSaveDTO dto) {
        requireDoctor(id);
        requireDepartment(dto.getDepartmentId());
        Doctor doctor = new Doctor();
        doctor.setId(id);
        applyDto(doctor, dto);
        doctorMapper.update(doctor);
        log.info("[医生] 编辑医生: doctorId={}", id);
        return getById(id);
    }

    /**
     * 启用/停用医生
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        requireDoctor(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态值只能为 0 或 1");
        }
        doctorMapper.updateStatus(id, status);
        log.info("[医生] 状态变更: doctorId={}, status={}", id, status);
    }

    // ==================== 私有方法 ====================

    private Doctor requireDoctor(Long id) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        return doctor;
    }

    private void requireDepartment(Long departmentId) {
        if (departmentId == null || departmentMapper.selectById(departmentId) == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "科室不存在");
        }
    }

    private void applyDto(Doctor doctor, DoctorSaveDTO dto) {
        doctor.setUserId(dto.getUserId());
        doctor.setName(dto.getName());
        doctor.setGender(dto.getGender());
        doctor.setPhone(dto.getPhone());
        doctor.setDepartmentId(dto.getDepartmentId());
        doctor.setTitle(dto.getTitle());
        doctor.setSpecialty(dto.getSpecialty());
        doctor.setIntroduction(dto.getIntroduction());
    }

    private List<DoctorVO> toVOList(List<Doctor> doctors) {
        if (doctors == null || doctors.isEmpty()) {
            return List.of();
        }
        List<Long> deptIds = doctors.stream()
                .map(Doctor::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> deptNames = departmentMapper.selectByIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDeptName));
        return doctors.stream()
                .map(d -> toVO(d, deptNames.get(d.getDepartmentId())))
                .collect(Collectors.toList());
    }

    private DoctorVO toVO(Doctor doctor) {
        Department dept = doctor.getDepartmentId() != null
                ? departmentMapper.selectById(doctor.getDepartmentId()) : null;
        return toVO(doctor, dept != null ? dept.getDeptName() : null);
    }

    private DoctorVO toVO(Doctor doctor, String deptName) {
        return DoctorVO.builder()
                .id(doctor.getId())
                .userId(doctor.getUserId())
                .name(doctor.getName())
                .gender(doctor.getGender())
                .phone(doctor.getPhone())
                .departmentId(doctor.getDepartmentId())
                .departmentName(deptName)
                .title(doctor.getTitle())
                .specialty(doctor.getSpecialty())
                .introduction(doctor.getIntroduction())
                .status(doctor.getStatus())
                .createTime(doctor.getCreateTime())
                .build();
    }
}
