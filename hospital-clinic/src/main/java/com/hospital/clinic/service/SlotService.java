package com.hospital.clinic.service;

import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.entity.Slot;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.mapper.SlotMapper;
import com.hospital.clinic.vo.SlotVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 号源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotMapper slotMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;

    /**
     * 根据排班自动生成号源（每 slotDuration 分钟一个时隙）
     */
    public void generateSlots(Schedule schedule) {
        int slotDuration = schedule.getSlotDuration();
        LocalTime start = schedule.getPeriodStart();
        LocalTime end = schedule.getPeriodEnd();

        List<Slot> slots = new ArrayList<>();
        int seq = 1;
        LocalTime cursor = start;
        while (cursor.plusMinutes(slotDuration).compareTo(end) <= 0 && seq <= schedule.getTotalSlots()) {
            Slot slot = new Slot();
            slot.setScheduleId(schedule.getId());
            slot.setSlotSeq(seq);
            slot.setSlotStart(cursor);
            slot.setSlotEnd(cursor.plusMinutes(slotDuration));
            slot.setStatus("AVAILABLE");
            slot.setVersion(0);
            slots.add(slot);
            cursor = cursor.plusMinutes(slotDuration);
            seq++;
        }

        if (!slots.isEmpty()) {
            slotMapper.batchInsert(slots);
            log.info("[号源] 自动生成号源: scheduleId={}, count={}", schedule.getId(), slots.size());
        }
    }

    /**
     * 查询某排班的可用号源列表
     */
    public List<SlotVO> listBySchedule(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
        }

        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Department dept = departmentMapper.selectById(schedule.getDepartmentId());

        List<Slot> slots = slotMapper.selectAvailableBySchedule(scheduleId);
        return slots.stream().map(s -> toVO(s, schedule, doctor, dept)).collect(Collectors.toList());
    }

    /**
     * 按科室+日期查询可用号源（批量加载避免 N+1）
     */
    public List<SlotVO> listAvailable(Long departmentId, LocalDate date) {
        List<Slot> slots = slotMapper.selectAvailableByDeptAndDate(departmentId, date);
        if (slots.isEmpty()) {
            return List.of();
        }

        // 批量加载所有关联的排班
        Set<Long> scheduleIds = slots.stream().map(Slot::getScheduleId).collect(Collectors.toSet());
        List<Schedule> schedules = scheduleMapper.selectByIds(new ArrayList<>(scheduleIds));
        Map<Long, Schedule> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(Schedule::getId, s -> s));

        // 批量加载所有关联的医生
        Set<Long> doctorIds = schedules.stream().map(Schedule::getDoctorId).collect(Collectors.toSet());
        List<Doctor> doctors = doctorMapper.selectByIds(new ArrayList<>(doctorIds));
        Map<Long, Doctor> doctorMap = doctors.stream()
                .collect(Collectors.toMap(Doctor::getId, d -> d));

        // 科室只加载一次（同一科室下所有排班共享）
        Department dept = departmentMapper.selectById(departmentId);

        return slots.stream().map(s -> {
            Schedule schedule = scheduleMap.get(s.getScheduleId());
            Doctor doctor = schedule != null ? doctorMap.get(schedule.getDoctorId()) : null;
            return toVO(s, schedule, doctor, dept);
        }).collect(Collectors.toList());
    }

    /**
     * 查询某排班剩余可用号源数
     */
    public int countAvailableSlots(Long scheduleId) {
        return slotMapper.countAvailableBySchedule(scheduleId);
    }

    // ==================== 内部方法 ====================

    /**
     * 乐观锁扣减号源（内部调用，不暴露到 Controller）
     */
    public boolean deductSlot(Long slotId, Integer version) {
        int rows = slotMapper.updateStatusWithVersion(slotId, "BOOKED", version);
        return rows > 0;
    }

    /**
     * 释放号源（带版本号乐观锁，防止重复释放）
     */
    public void releaseSlot(Long slotId, Integer version) {
        int rows = slotMapper.releaseSlot(slotId, version);
        if (rows == 0) {
            log.warn("[号源] 号源释放失败（版本不匹配或状态异常）: slotId={}, version={}", slotId, version);
        } else {
            log.info("[号源] 号源已释放: slotId={}, version={}", slotId, version);
        }
    }

    // ==================== 实体 → VO 转换 ====================

    private SlotVO toVO(Slot s, Schedule schedule, Doctor doctor, Department dept) {
        return SlotVO.builder()
                .id(s.getId())
                .scheduleId(s.getScheduleId())
                .doctorId(schedule != null ? schedule.getDoctorId() : null)
                .doctorName(doctor != null ? doctor.getName() : null)
                .doctorTitle(doctor != null ? doctor.getTitle() : null)
                .departmentId(schedule != null ? schedule.getDepartmentId() : null)
                .departmentName(dept != null ? dept.getDeptName() : null)
                .scheduleDate(schedule != null ? schedule.getScheduleDate() : null)
                .period(schedule != null ? schedule.getPeriod() : null)
                .slotSeq(s.getSlotSeq())
                .slotStart(s.getSlotStart())
                .slotEnd(s.getSlotEnd())
                .status(s.getStatus())
                .registerFee(schedule != null ? schedule.getRegisterFee() : null)
                .build();
    }
}
