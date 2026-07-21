package com.hospital.clinic.service;

import com.hospital.clinic.dto.ScheduleCreateDTO;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.vo.ScheduleVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 排班管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final SlotService slotService;

    /**
     * 创建排班（同步生成号源）
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleCreateDTO dto) {
        // 1. 校验医生
        Doctor doctor = doctorMapper.selectById(dto.getDoctorId());
        if (doctor == null || doctor.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        // 2. 校验科室
        Department dept = departmentMapper.selectById(dto.getDepartmentId());
        if (dept == null || dept.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.DEPARTMENT_NOT_FOUND);
        }
        // 3. 校验重复排班
        Schedule existing = scheduleMapper.selectByDoctorDatePeriod(
                dto.getDoctorId(), dto.getScheduleDate(), dto.getPeriod());
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "该医生在该时段已有排班");
        }

        // 4. 计算号源
        int slotDuration = dto.getSlotDuration() != null ? dto.getSlotDuration() : 10;
        int totalMinutes = (dto.getPeriodEnd().toSecondOfDay() - dto.getPeriodStart().toSecondOfDay()) / 60;
        int totalSlots = dto.getTotalSlots() != null ? dto.getTotalSlots() : totalMinutes / slotDuration;
        if (totalSlots <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "号源数量必须大于0，请检查时段设置");
        }

        // 5. 创建排班
        Schedule schedule = new Schedule();
        schedule.setDoctorId(dto.getDoctorId());
        schedule.setDepartmentId(dto.getDepartmentId());
        schedule.setScheduleDate(dto.getScheduleDate());
        schedule.setPeriod(dto.getPeriod());
        schedule.setPeriodStart(dto.getPeriodStart());
        schedule.setPeriodEnd(dto.getPeriodEnd());
        schedule.setTotalSlots(totalSlots);
        schedule.setSlotDuration(slotDuration);
        schedule.setRegisterFee(dto.getRegisterFee() != null ? dto.getRegisterFee() : BigDecimal.ZERO);
        schedule.setStatus(1);
        scheduleMapper.insert(schedule);
        log.info("[排班] 创建成功: id={}, doctor={}, date={} {}",
                schedule.getId(), doctor.getName(), dto.getScheduleDate(), dto.getPeriod());

        // 6. 自动生成号源
        slotService.generateSlots(schedule);
        return toVO(schedule, doctor, dept, totalSlots);
    }

    /**
     * 排班日历视图（按科室+日期范围查询）
     */
    public List<ScheduleVO> calendar(Long departmentId, LocalDate startDate, LocalDate endDate) {
        List<Schedule> schedules = scheduleMapper.selectByDeptAndDateRange(
                departmentId, startDate, endDate);
        return schedules.stream().map(s -> {
            Doctor doctor = doctorMapper.selectById(s.getDoctorId());
            Department dept = departmentMapper.selectById(s.getDepartmentId());
            int availableSlots = slotService.countAvailableSlots(s.getId());
            return toVO(s, doctor, dept, availableSlots);
        }).collect(Collectors.toList());
    }

    /**
     * 排班详情
     */
    public ScheduleVO getById(Long id) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
        }
        Doctor doctor = doctorMapper.selectById(s.getDoctorId());
        Department dept = departmentMapper.selectById(s.getDepartmentId());
        int availableSlots = slotService.countAvailableSlots(s.getId());
        return toVO(s, doctor, dept, availableSlots);
    }

    /**
     * 取消排班
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
        }
        scheduleMapper.updateStatus(id, 0);
        log.info("[排班] 排班已取消: id={}", id);
    }

    // ==================== 实体 → VO 转换 ====================

    private ScheduleVO toVO(Schedule s, Doctor doctor, Department dept, int availableSlots) {
        return ScheduleVO.builder()
                .id(s.getId())
                .doctorId(s.getDoctorId())
                .doctorName(doctor != null ? doctor.getName() : null)
                .doctorTitle(doctor != null ? doctor.getTitle() : null)
                .departmentId(s.getDepartmentId())
                .departmentName(dept != null ? dept.getDeptName() : null)
                .scheduleDate(s.getScheduleDate())
                .period(s.getPeriod())
                .periodStart(s.getPeriodStart())
                .periodEnd(s.getPeriodEnd())
                .totalSlots(s.getTotalSlots())
                .availableSlots(availableSlots)
                .slotDuration(s.getSlotDuration())
                .registerFee(s.getRegisterFee())
                .status(s.getStatus())
                .createTime(s.getCreateTime())
                .build();
    }
}
