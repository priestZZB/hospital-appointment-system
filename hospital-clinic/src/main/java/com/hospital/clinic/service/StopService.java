package com.hospital.clinic.service;

import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.entity.StopApplication;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.mapper.StopApplicationMapper;
import com.hospital.clinic.vo.StopApplicationVO;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PaymentFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 停诊管理服务
 * <p>
 * 医生发起停诊申请（仅未来 48h）→ 管理员审批 → 取消待签到预约 + 退款 + 释放号源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopService {

    private final StopApplicationMapper stopApplicationMapper;
    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentService appointmentService;
    private final PaymentFeignClient paymentFeignClient;
    private final DoctorMapper doctorMapper;
    private final com.hospital.common.feign.PatientFeignClient patientFeignClient;

    /**
     * 停诊申请
     *
     * @param userId       当前登录用户ID（来自 auth-service）
     * @param scheduleId   排班ID
     * @param applyReason  停诊原因
     * @return 停诊申请 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public StopApplicationVO apply(Long userId, Long scheduleId, String applyReason) {
        // 通过 userId 查找对应的 doctor 记录
        com.hospital.clinic.entity.Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        Long doctorId = doctor.getId();
        // 1. 查询排班
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
        }

        // 2. 校验：仅未来 48 小时内可申请
        LocalDateTime scheduleStart = LocalDateTime.of(schedule.getScheduleDate(), schedule.getPeriodStart());
        LocalDateTime now = LocalDateTime.now();
        long hoursUntil = java.time.Duration.between(now, scheduleStart).toHours();
        if (hoursUntil < 0) {
            throw new BusinessException(ErrorCodeEnum.STOP_NOT_IN_48H, "排班已过期，不可申请停诊");
        }
        if (hoursUntil > 48) {
            throw new BusinessException(ErrorCodeEnum.STOP_NOT_IN_48H, "仅可申请未来48小时内的停诊");
        }

        // 3. 校验：该排班无已签到/就诊中的预约（冲突检查）
        List<Appointment> appointments = appointmentMapper.selectByScheduleId(scheduleId);
        boolean hasConflict = appointments.stream().anyMatch(a -> {
            String vs = a.getVisitStatus();
            return "CHECKED_IN".equals(vs) || "WAITING".equals(vs)
                    || "CALLED".equals(vs) || "IN_PROGRESS".equals(vs) || "COMPLETED".equals(vs);
        });
        if (hasConflict) {
            throw new BusinessException(ErrorCodeEnum.STOP_CONFLICT);
        }

        // 4. 检查是否已有待审批的申请
        StopApplication existing = stopApplicationMapper.selectByScheduleId(scheduleId);
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "该排班已有待审批的停诊申请");
        }

        // 5. 创建停诊申请
        StopApplication application = new StopApplication();
        application.setScheduleId(scheduleId);
        application.setDoctorId(doctorId);
        application.setApplyReason(applyReason);
        application.setStatus("PENDING");
        stopApplicationMapper.insert(application);
        log.info("[停诊] 申请已提交: id={}, scheduleId={}, doctorId={}", application.getId(), scheduleId, doctorId);

        return toVO(application);
    }

    /**
     * 管理员审批
     * <p>
     * 通过 → 取消该排班下所有待签到预约 → 退款 → 释放号源 → 通知
     * 驳回 → 填写审批意见
     *
     * @param applicationId 申请 ID
     * @param adminId       审批人 ID
     * @param action        APPROVE / REJECT
     * @param comment       审批意见
     * @return 停诊申请 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public StopApplicationVO approve(Long applicationId, Long adminId, String action, String comment) {
        StopApplication application = stopApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "停诊申请不存在");
        }
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.STOP_ALREADY_PROCESSED);
        }

        if ("REJECT".equals(action)) {
            stopApplicationMapper.updateApproval(applicationId, "REJECTED", comment,
                    adminId, 0, BigDecimal.ZERO, "PENDING");
            log.info("[停诊] 已驳回: id={}", applicationId);
            return toVO(stopApplicationMapper.selectById(applicationId));
        }

        // ========== 审批通过 ==========
        // 查询该排班下所有待签到的预约（PENDING_PAY 或 PAID 但未签到）
        List<Appointment> appointments = appointmentMapper.selectByScheduleId(application.getScheduleId());
        List<Appointment> cancellable = appointments.stream()
                .filter(a -> "PENDING_PAY".equals(a.getOrderStatus()) || "PAID".equals(a.getOrderStatus()))
                .filter(a -> a.getVisitStatus() == null
                        || "CHECKED_IN".equals(a.getVisitStatus()) == false)
                .toList();

        int affectedCount = 0;
        BigDecimal refundTotal = BigDecimal.ZERO;

        for (Appointment appt : cancellable) {
            // 取消预约 + 释放号源
            if ("PENDING_PAY".equals(appt.getOrderStatus())) {
                appointmentService.cancel(appt.getId(), "医生停诊");
                affectedCount++;
            } else if ("PAID".equals(appt.getOrderStatus())) {
                // 退款
                try {
                    Map<String, Object> refundDTO = new HashMap<>();
                    refundDTO.put("appointmentId", appt.getId());
                    refundDTO.put("refundReason", "医生停诊");
                    refundDTO.put("refundType", "DOCTOR_STOP");
                    paymentFeignClient.refund(refundDTO);
                    affectedCount++;
                    refundTotal = refundTotal.add(appt.getRegisterFee());
                } catch (Exception e) {
                    log.error("[停诊] 退款失败: appointmentId={}", appt.getId(), e);
                    throw new BusinessException(ErrorCodeEnum.REFUND_FAILED, "退款失败");
                }
            }
        }

        // 取消排班
        scheduleMapper.updateStatus(application.getScheduleId(), 0);

        // 更新审批状态
        stopApplicationMapper.updateApproval(applicationId, "APPROVED", comment,
                adminId, affectedCount, refundTotal, "PENDING");
        log.info("[停诊] 已通过: id={}, 受影响{}人, 退款{}元", applicationId, affectedCount, refundTotal);

        return toVO(stopApplicationMapper.selectById(applicationId));
    }

    /**
     * 查询停诊申请列表（按状态）
     */
    public List<StopApplicationVO> listByStatus(String status, long offset, Integer limit) {
        List<StopApplication> list = stopApplicationMapper.selectByStatus(status, (int) offset, limit);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 查询某医生的停诊申请列表
     */
    public List<StopApplicationVO> listByDoctor(Long doctorId, long offset, Integer limit) {
        List<StopApplication> list = stopApplicationMapper.selectByDoctorId(doctorId, (int) offset, limit);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ==================== 实体 → VO ====================

    private StopApplicationVO toVO(StopApplication a) {
        return StopApplicationVO.builder()
                .id(a.getId()).scheduleId(a.getScheduleId()).doctorId(a.getDoctorId())
                .applyReason(a.getApplyReason()).status(a.getStatus())
                .approveComment(a.getApproveComment()).approvedBy(a.getApprovedBy())
                .approveTime(a.getApproveTime()).affectedCount(a.getAffectedCount())
                .refundTotal(a.getRefundTotal()).createTime(a.getCreateTime())
                .updateTime(a.getUpdateTime()).build();
    }
}
