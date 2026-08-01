package com.hospital.clinic.service;

import com.hospital.clinic.dto.CheckinDTO;
import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.entity.Checkin;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.CheckinMapper;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.vo.CheckinVO;
import com.hospital.clinic.vo.QueueStatusVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 签到服务
 * <p>
 * 患者到院签到 → 进入科室排队队列（Redis Sorted Set）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinMapper checkinMapper;
    private final AppointmentMapper appointmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final DepartmentMapper departmentMapper;
    private final PatientFeignClient patientFeignClient;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String QUEUE_KEY_PREFIX = "queue:dept:";

    /**
     * 患者签到
     * <p>
     * 校验：预约已支付 → 当前时间在号源时段前30min~后30min窗口内 → 未重复签到
     * 签到后：插入 checkin 记录 → 加入 Redis ZSet 排队队列 → 更新 appointment.visit_status
     *
     * @param patientId 患者 ID
     * @param dto       签到请求
     * @return 签到记录 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckinVO checkin(Long userId, CheckinDTO dto) {
        // 1. 通过 userId 查询 patientId（auth userId ≠ patient db id）
        Long patientId;
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED, "患者档案不存在");
            }
            Object pidObj = patientInfo.get("id");
            if (pidObj == null) {
                throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED, "患者信息异常");
            }
            patientId = ((Number) pidObj).longValue();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[签到] 查询患者信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR, "患者信息服务不可用");
        }

        // 2. 查询预约
        Appointment appointment = appointmentMapper.selectById(dto.getAppointmentId());
        if (appointment == null) {
            throw new BusinessException(ErrorCodeEnum.APPOINTMENT_NOT_FOUND);
        }
        if (!Objects.equals(appointment.getPatientId(), patientId)) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权操作他人预约");
        }
        if (!"PAID".equals(appointment.getOrderStatus())) {
            throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_CHECKED_IN, "仅已支付的预约可签到");
        }

        // 2. 校验签到时间窗口（号源开始前 30 分钟 至 后 30 分钟）
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
        }
        LocalDateTime slotStart = LocalDateTime.of(schedule.getScheduleDate(),
                schedule.getPeriodStart());
        LocalDateTime windowStart = slotStart.minusMinutes(30);
        LocalDateTime windowEnd = slotStart.plusMinutes(30);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(windowStart)) {
            throw new BusinessException(ErrorCodeEnum.NOT_CHECKIN_TIME);
        }
        if (now.isAfter(windowEnd)) {
            throw new BusinessException(ErrorCodeEnum.CHECKIN_EXPIRED);
        }

        // 3. 防止重复签到
        Checkin existing = checkinMapper.selectByAppointmentId(appointment.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_CHECKED_IN);
        }

        // 4. 创建签到记录
        Checkin checkin = new Checkin();
        checkin.setAppointmentId(appointment.getId());
        checkin.setPatientId(patientId);
        checkin.setDepartmentId(appointment.getDepartmentId());
        checkin.setDoctorId(appointment.getDoctorId());
        checkin.setCheckinTime(now);
        checkin.setQueueStatus("WAITING");
        checkin.setCallCount(0);
        checkinMapper.insert(checkin);
        log.info("[签到] 签到成功: checkinId={}, patientId={}, deptId={}",
                checkin.getId(), patientId, appointment.getDepartmentId());

        // 5. 加入 Redis 排队队列（score = 签到时间戳毫秒）
        String queueKey = QUEUE_KEY_PREFIX + appointment.getDepartmentId();
        stringRedisTemplate.opsForZSet().add(queueKey, String.valueOf(checkin.getId()),
                (double) System.currentTimeMillis());

        // 6. 更新 appointment.visit_status（仅当 visit_status 仍为 null 时更新，防止已叫号/已就诊被覆盖）
        int rows = appointmentMapper.updateVisitStatus(appointment.getId(), "CHECKED_IN", null);
        if (rows == 0) {
            log.warn("[签到] visit_status 已被其他操作更新: appointmentId={}", appointment.getId());
        }

        return toVO(checkin);
    }

    /**
     * 查询排队状态
     * <p>
     * 通过 Redis ZRANK 获取当前患者前面还有多少人。
     *
     * @param checkinId 签到 ID
     * @return 排队状态
     */
    public QueueStatusVO getQueueStatus(Long checkinId) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "签到记录不存在");
        }

        String queueKey = QUEUE_KEY_PREFIX + checkin.getDepartmentId();
        Long rank = stringRedisTemplate.opsForZSet().rank(queueKey, String.valueOf(checkinId));
        Long total = stringRedisTemplate.opsForZSet().size(queueKey);
        if (total == null) total = 0L;

        Department dept = departmentMapper.selectById(checkin.getDepartmentId());

        return QueueStatusVO.builder()
                .checkinId(checkinId)
                .deptName(dept != null ? dept.getDeptName() : null)
                .totalWaiting(total)
                .aheadCount(rank != null ? rank : 0)
                .queueStatus(checkin.getQueueStatus())
                .checkinTime(checkin.getCheckinTime())
                .build();
    }

    // ==================== 实体 → VO ====================

    private CheckinVO toVO(Checkin c) {
        return CheckinVO.builder()
                .id(c.getId())
                .appointmentId(c.getAppointmentId())
                .patientId(c.getPatientId())
                .departmentId(c.getDepartmentId())
                .doctorId(c.getDoctorId())
                .checkinTime(c.getCheckinTime())
                .queueStatus(c.getQueueStatus())
                .callTime(c.getCallTime())
                .callCount(c.getCallCount())
                .consultRoom(c.getConsultRoom())
                .createTime(c.getCreateTime())
                .build();
    }
}
