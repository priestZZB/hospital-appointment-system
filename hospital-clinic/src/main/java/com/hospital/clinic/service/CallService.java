package com.hospital.clinic.service;

import com.hospital.clinic.entity.Checkin;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.CheckinMapper;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.vo.CallMessageVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.Map;

/**
 * 叫号服务
 * <p>
 * ZPOPMIN 取最早签到 → 更新状态 → WebSocket 广播。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CheckinMapper checkinMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final AppointmentMapper appointmentMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final PatientFeignClient patientFeignClient;

    private static final String QUEUE_KEY_PREFIX = "queue:dept:";

    /**
     * 医生叫号（下一号）
     * <p>
     * ZPOPMIN 取最早签到 → 更新 checkin 状态为 CALLED → WebSocket 广播
     *
     * @param departmentId 科室ID
     * @param consultRoom  诊室号
     * @return 被叫号签到信息
     */
    @Transactional(rollbackFor = Exception.class)
    public CallMessageVO callNext(Long departmentId, String consultRoom, Long userId) {
        // 权限校验：仅医生或管理员可叫号
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅医生可执行叫号操作");
        }
        // 校验医生属于该科室
        com.hospital.clinic.entity.Doctor callingDoctor = doctorMapper.selectByUserId(userId);
        if (callingDoctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        if (!Objects.equals(callingDoctor.getDepartmentId(), departmentId)) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "您不属于该科室");
        }

        // 1. ZPOPMIN：取出签到时间最早的患者
        String queueKey = QUEUE_KEY_PREFIX + departmentId;
        @SuppressWarnings("unchecked")
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> popped =
                (Set) stringRedisTemplate.opsForZSet().popMin(queueKey, 1);
        if (popped == null || popped.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.QUEUE_EMPTY);
        }
        String member = popped.iterator().next().getValue();
        Long checkinId = Long.parseLong(member);

        // 2. 查询签到记录
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "签到记录不存在");
        }

        // 3. 更新叫号信息
        int newCallCount = (checkin.getCallCount() != null ? checkin.getCallCount() : 0) + 1;
        checkinMapper.updateCallInfo(checkinId, "CALLED", newCallCount, consultRoom);

        // 4. 更新 appointment.visit_status
        appointmentMapper.updateVisitStatus(checkin.getAppointmentId(), "CALLED", null);

        // 5. 组装推送消息
        Department dept = departmentMapper.selectById(departmentId);
        String patientName = resolvePatientName(checkin.getPatientId());

        CallMessageVO message = CallMessageVO.builder()
                .type("CALL_NUMBER")
                .checkinId(checkin.getId())
                .appointmentId(checkin.getAppointmentId())
                .patientId(checkin.getPatientId())
                .deptId(departmentId)
                .deptName(dept != null ? dept.getDeptName() : null)
                .doctorName(callingDoctor != null ? callingDoctor.getName() : null)
                .consultRoom(consultRoom)
                .patientName(patientName)
                .queueNumber(newCallCount)
                .timestamp(System.currentTimeMillis())
                .build();

        // 6. WebSocket 科室维度广播
        String destination = "/topic/call/" + departmentId;
        messagingTemplate.convertAndSend(destination, message);
        log.info("[叫号] 科室={}, 患者={}, 诊室={}, 第{}次叫号",
                departmentId, checkin.getPatientId(), consultRoom, newCallCount);

        return message;
    }

    /**
     * 重呼
     * <p>
     * 对已叫号但未响应的患者再次推送叫号通知。
     *
     * @param checkinId 签到记录 ID
     * @return 叫号消息
     */
    @Transactional(rollbackFor = Exception.class)
    public CallMessageVO recall(Long checkinId, String consultRoom, Long userId) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "签到记录不存在");
        }
        // 权限校验：仅医生或管理员可重呼，且医生须属于该科室
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅医生可执行重呼操作");
        }
        Doctor callingDoctor = doctorMapper.selectByUserId(userId);
        if (callingDoctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        if (!Objects.equals(callingDoctor.getDepartmentId(), checkin.getDepartmentId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "您不属于该科室");
        }
        if (!"CALLED".equals(checkin.getQueueStatus()) && !"RE_CALLED".equals(checkin.getQueueStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已叫号的患者可重呼");
        }

        int newCallCount = (checkin.getCallCount() != null ? checkin.getCallCount() : 0) + 1;
        checkinMapper.updateCallInfo(checkinId, "RE_CALLED", newCallCount, consultRoom);

        Department dept = departmentMapper.selectById(checkin.getDepartmentId());
        Doctor doctor = doctorMapper.selectById(checkin.getDoctorId());

        CallMessageVO message = CallMessageVO.builder()
                .type("RECALL")
                .checkinId(checkin.getId())
                .appointmentId(checkin.getAppointmentId())
                .patientId(checkin.getPatientId())
                .deptId(checkin.getDepartmentId())
                .deptName(dept != null ? dept.getDeptName() : null)
                .doctorName(doctor != null ? doctor.getName() : null)
                .consultRoom(consultRoom)
                .patientName(resolvePatientName(checkin.getPatientId()))
                .queueNumber(newCallCount)
                .timestamp(System.currentTimeMillis())
                .build();

        String destination = "/topic/call/" + checkin.getDepartmentId();
        messagingTemplate.convertAndSend(destination, message);
        log.info("[重呼] checkinId={}, 第{}次叫号", checkinId, newCallCount);

        return message;
    }

    /**
     * 过号处理
     * <p>
     * 已叫号患者超时未响应 → 标记为 MISSED → 重新加入队尾（ZADD）。
     *
     * @param checkinId 签到记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markMissed(Long checkinId, Long userId) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "签到记录不存在");
        }
        // 权限校验：仅医生或管理员可处理过号，且医生须属于该科室
        if (!UserContext.isDoctorOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅医生可处理过号");
        }
        Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        if (!Objects.equals(doctor.getDepartmentId(), checkin.getDepartmentId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "您不属于该科室");
        }

        // 更新状态为 MISSED
        checkinMapper.updateRejoin(checkinId, "MISSED");

        // 重新加入排队队列（队尾）
        String queueKey = QUEUE_KEY_PREFIX + checkin.getDepartmentId();
        stringRedisTemplate.opsForZSet().add(queueKey, String.valueOf(checkinId),
                (double) System.currentTimeMillis());

        // 推送过号通知
        Department dept = departmentMapper.selectById(checkin.getDepartmentId());
        CallMessageVO message = CallMessageVO.builder()
                .type("MISSED")
                .checkinId(checkin.getId())
                .appointmentId(checkin.getAppointmentId())
                .patientId(checkin.getPatientId())
                .deptId(checkin.getDepartmentId())
                .deptName(dept != null ? dept.getDeptName() : null)
                .patientName(resolvePatientName(checkin.getPatientId()))
                .timestamp(System.currentTimeMillis())
                .build();
        messagingTemplate.convertAndSend("/topic/call/" + checkin.getDepartmentId(), message);
        log.info("[过号] checkinId={}, 已重新排队", checkinId);
    }

    private String resolvePatientName(Long patientId) {
        if (patientId == null) {
            return null;
        }
        try {
            Map<String, Object> info = patientFeignClient.getById(patientId);
            if (info != null && info.get("name") != null) {
                return String.valueOf(info.get("name"));
            }
        } catch (Exception e) {
            log.warn("[叫号] 患者姓名查询失败（使用占位）: patientId={}, error={}", patientId, e.getMessage());
        }
        return "患者" + patientId;
    }
}
