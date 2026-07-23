package com.hospital.clinic.service;

import cn.hutool.core.lang.UUID;
import com.hospital.clinic.dto.AppointmentSubmitDTO;
import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.entity.Slot;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.mapper.SlotMapper;
import com.hospital.clinic.vo.AppointmentVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.PaymentFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 预约挂号服务（核心）
 * <p>
 * 负责挂号下单、取消预约、预约查询等核心业务流程。
 * 使用 Redisson 分布式锁 + 乐观锁保证并发安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final SlotMapper slotMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final SlotService slotService;
    private final PatientFeignClient patientFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REPEAT_KEY_PREFIX = "repeat:appointment:";
    private static final String LOCK_KEY_PREFIX = "lock:slot:";
    private static final String APPOINTMENT_LOCK_KEY_PREFIX = "lock:appointment:";

    /**
     * 挂号下单（核心流程）
     *
     * <pre>
     * 1. Feign 调用 patient-service 校验实名认证
     * 2. 查询号源状态是否为 AVAILABLE
     * 3. Redis 检查是否重复挂号
     * 4. Redisson 分布式锁 + 乐观锁扣减号源 + 设置重复挂号键
     * 5. 插入 appointment 记录（PENDING_PAY）
     * 6. Feign 调用 payment-service 创建支付订单
     * 7. 释放分布式锁，返回预约信息
     * </pre>
     *
     * @param userId 用户 ID（来自 auth-service）
     * @param dto    挂号信息
     * @return 预约订单 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public AppointmentVO submit(Long userId, AppointmentSubmitDTO dto) {
        Long slotId = dto.getSlotId();
        Long scheduleId = dto.getScheduleId();

        // ========== 第1步：Feign 获取患者信息 + 校验实名状态 ==========
        Long patientId;
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED, "患者档案不存在，请联系管理员");
            }
            Object patientIdObj = patientInfo.get("id");
            if (patientIdObj == null) {
                throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED, "患者信息异常");
            }
            patientId = toLong(patientIdObj);

            Object verifyStatus = patientInfo.get("verifyStatus");
            if (verifyStatus == null || ((Number) verifyStatus).intValue() != 2) {
                throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[挂号] 远程调用 patient-service 失败: userId={}", userId, e);
            throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR, "患者信息服务不可用");
        }

        // ========== 第2步：查询号源 ==========
        Slot slot = slotMapper.selectById(slotId);
        if (slot == null || !"AVAILABLE".equals(slot.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_ENOUGH);
        }

        // 查询排班
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE);
        }
        // 校验 slot 与 schedule 的关联关系
        if (!slot.getScheduleId().equals(scheduleId)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "号源与排班信息不一致");
        }

        // ========== 第3步：Redisson 分布式锁 + 乐观锁扣减（锁内包含防重复校验） ==========
        String repeatKey = REPEAT_KEY_PREFIX + patientId + ":" + scheduleId;
        String lockKey = LOCK_KEY_PREFIX + slotId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            // 尝试加锁：最多等 5 秒，锁自动释放时间 10 秒
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "系统繁忙，请稍后重试");
            }

            // Redis 防重复挂号（在锁内检查，防止同一排班不同号源的并发请求）
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(repeatKey))) {
                throw new BusinessException(ErrorCodeEnum.DUPLICATE_APPOINTMENT);
            }

            // 乐观锁扣减号源
            boolean deducted = slotService.deductSlot(slotId, slot.getVersion());
            if (!deducted) {
                throw new BusinessException(ErrorCodeEnum.SLOT_NOT_ENOUGH);
            }

            // Redis 设置重复挂号键（在锁内设置，防止并发请求绕过检查）
            long ttlSeconds = calculateTTL(schedule.getScheduleDate(), slot.getSlotStart());
            if (ttlSeconds > 0) {
                stringRedisTemplate.opsForValue().set(repeatKey, "1", Duration.ofSeconds(ttlSeconds));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "挂号操作被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // ========== 第5步：创建预约记录 ==========
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Department dept = departmentMapper.selectById(schedule.getDepartmentId());

        Appointment appointment = new Appointment();
        appointment.setAppointmentNo(generateAppointmentNo());
        appointment.setPatientId(patientId);
        appointment.setSlotId(slotId);
        appointment.setScheduleId(scheduleId);
        appointment.setDoctorId(schedule.getDoctorId());
        appointment.setDepartmentId(schedule.getDepartmentId());
        appointment.setAppointmentDate(schedule.getScheduleDate());
        appointment.setPeriod(schedule.getPeriod());
        appointment.setSlotSeq(slot.getSlotSeq());
        appointment.setRegisterFee(schedule.getRegisterFee());
        appointment.setOrderStatus("PENDING_PAY");
        appointment.setVisitStatus(null);
        appointment.setIsRevisit(0);
        appointmentMapper.insert(appointment);
        log.info("[挂号] 预约记录已创建: appointmentId={}, appointmentNo={}, patientId={}",
                appointment.getId(), appointment.getAppointmentNo(), patientId);

        // ========== 第6步：Feign 调用 payment-service 创建支付订单 ==========
        Long paymentOrderId = null;
        String paymentOrderNo = null;
        try {
            Map<String, Object> orderDTO = new HashMap<>();
            orderDTO.put("appointmentId", appointment.getId());
            orderDTO.put("patientId", patientId);
            orderDTO.put("amount", schedule.getRegisterFee());
            orderDTO.put("orderType", "REGISTRATION");
            Map<String, Object> payResult = paymentFeignClient.createOrder(orderDTO);
            if (payResult != null) {
                paymentOrderId = toLong(payResult.get("id"));
                paymentOrderNo = (String) payResult.get("orderNo");
            }
        } catch (Exception e) {
            // 支付订单创建失败：直接抛异常，@Transactional 会回滚预约记录和号源扣减
            // （Redis 重复键在 step7 设置，此处尚未执行，无需清理）
            log.error("[挂号] 创建支付订单失败，事务回滚: appointmentId={}", appointment.getId(), e);
            throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR, "支付服务暂不可用，请稍后重试");
        }

        // ========== 第7步：组装返回 ==========
        return buildVO(appointment, doctor, dept, slot, paymentOrderId, paymentOrderNo);
    }

    /**
     * 取消预约
     * <p>
     * 仅允许取消 PENDING_PAY 或 PAID 状态的预约。
     * 使用 Redisson 分布式锁保证并发安全，在锁内重新读取预约状态，
     * 取消后根据实际状态释放号源或触发退款。
     *
     * @param appointmentId 预约 ID
     * @param reason        取消原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long appointmentId, String reason) {
        String lockKey = APPOINTMENT_LOCK_KEY_PREFIX + appointmentId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "系统繁忙，请稍后重试");
            }

            // 在锁内重新读取预约，保证状态最新
            Appointment appointment = appointmentMapper.selectById(appointmentId);
            if (appointment == null) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_NOT_FOUND);
            }

            String status = appointment.getOrderStatus();
            if ("CANCELLED".equals(status) || "REFUNDED".equals(status) || "TIMEOUT".equals(status)) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_CANNOT_CANCEL, "该预约已取消或已过期");
            }

            // 取消预约（传入预期状态，防止竞态覆盖）
            int rows = appointmentMapper.cancel(appointmentId, "CANCELLED", reason, status);
            if (rows == 0) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_CANNOT_CANCEL, "该预约状态已变更，请刷新重试");
            }
            log.info("[挂号] 预约已取消: appointmentId={}, reason={}", appointmentId, reason);

            // PENDING_PAY：读取号源版本号后乐观锁释放
            // PAID：由 payment-service 退款回调统一释放号源
            if ("PENDING_PAY".equals(status)) {
                Slot slot = slotMapper.selectById(appointment.getSlotId());
                if (slot != null && "BOOKED".equals(slot.getStatus())) {
                    slotService.releaseSlot(appointment.getSlotId(), slot.getVersion());
                }
            }

            // 清除重复挂号键
            String repeatKey = REPEAT_KEY_PREFIX + appointment.getPatientId() + ":" + appointment.getScheduleId();
            stringRedisTemplate.delete(repeatKey);

            // 如果已支付，调用 payment 退款（payment 内部会回调 release-slot）
            if ("PAID".equals(status)) {
                Map<String, Object> refundDTO = new HashMap<>();
                refundDTO.put("appointmentId", appointmentId);
                refundDTO.put("refundReason", reason != null ? reason : "患者取消预约");
                refundDTO.put("refundType", "PATIENT_CANCEL");
                // 退款失败则抛异常回滚，保证数据一致性
                paymentFeignClient.refund(refundDTO);
                log.info("[挂号] 已触发退款: appointmentId={}", appointmentId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "取消预约操作被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 我的预约列表（联表查询，避免 N+1）
     */
    public List<AppointmentVO> listByPatient(Long userId) {
        // userId → patientId
        Long patientId;
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                return List.of();
            }
            patientId = toLong(patientInfo.get("id"));
        } catch (Exception e) {
            log.warn("[挂号] 查询患者信息失败: userId={}", userId, e);
            return List.of();
        }
        return appointmentMapper.selectByPatientIdWithDetail(patientId);
    }

    /**
     * 预约详情（联表查询，避免 N+1）
     */
    public AppointmentVO getById(Long appointmentId) {
        AppointmentVO vo = appointmentMapper.selectByIdWithDetail(appointmentId);
        if (vo == null) {
            throw new BusinessException(ErrorCodeEnum.APPOINTMENT_NOT_FOUND);
        }
        return vo;
    }

    // ==================== 内部回调方法（供 Feign / Payment 调用） ====================

    /**
     * 确认号源锁定（支付成功后 payment-service 回调）
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmLock(Long appointmentId) {
        int rows = appointmentMapper.updateOrderStatus(appointmentId, "PAID", "PENDING_PAY");
        if (rows == 0) {
            log.warn("[挂号] 确认锁定失败（状态不匹配，可能已被超时关单）: appointmentId={}", appointmentId);
            return;
        }
        log.info("[挂号] 支付成功，号源已确认锁定: appointmentId={}", appointmentId);
    }

    /**
     * 释放号源（超时关单后 payment-service 回调）
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseSlotByPayment(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return;
        }
        // 仅 PENDING_PAY 可转为 TIMEOUT，防止覆盖已支付的预约
        int rows = appointmentMapper.updateOrderStatus(appointmentId, "TIMEOUT", "PENDING_PAY");
        if (rows == 0) {
            log.info("[挂号] 号源释放跳过（已被支付或已取消）: appointmentId={}", appointmentId);
            return;
        }
        // 读取号源版本号，乐观锁释放
        Slot slot = slotMapper.selectById(appointment.getSlotId());
        if (slot != null && "BOOKED".equals(slot.getStatus())) {
            slotService.releaseSlot(appointment.getSlotId(), slot.getVersion());
        }
        String repeatKey = REPEAT_KEY_PREFIX + appointment.getPatientId() + ":" + appointment.getScheduleId();
        stringRedisTemplate.delete(repeatKey);
        log.info("[挂号] 超时关单，号源已释放: appointmentId={}", appointmentId);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成预约编号：APT + 时间戳(17位) + 随机(8位)
     */
    private String generateAppointmentNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = UUID.fastUUID().toString().substring(0, 8).toUpperCase();
        return "APT" + timestamp + random;
    }

    /**
     * 计算重复挂号键 TTL（到号源开始时间为止）
     */
    private long calculateTTL(LocalDate scheduleDate, LocalTime slotStart) {
        LocalDateTime slotDateTime = LocalDateTime.of(scheduleDate, slotStart);
        long ttl = Duration.between(LocalDateTime.now(), slotDateTime).getSeconds();
        return Math.max(ttl, 60); // 最少保留 60 秒
    }

    /**
     * 将 Object 安全转为 Long
     */
    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 组装完整 VO
     */
    private AppointmentVO buildVO(Appointment a, Doctor doctor, Department dept, Slot slot,
                                  Long paymentOrderId, String paymentOrderNo) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(a.getId());
        vo.setAppointmentNo(a.getAppointmentNo());
        vo.setPatientId(a.getPatientId());
        vo.setSlotId(a.getSlotId());
        vo.setScheduleId(a.getScheduleId());
        vo.setDoctorId(a.getDoctorId());
        vo.setDoctorName(doctor != null ? doctor.getName() : null);
        vo.setDoctorTitle(doctor != null ? doctor.getTitle() : null);
        vo.setDepartmentId(a.getDepartmentId());
        vo.setDepartmentName(dept != null ? dept.getDeptName() : null);
        vo.setAppointmentDate(a.getAppointmentDate());
        vo.setPeriod(a.getPeriod());
        vo.setSlotSeq(a.getSlotSeq());
        vo.setSlotStart(slot.getSlotStart());
        vo.setSlotEnd(slot.getSlotEnd());
        vo.setRegisterFee(a.getRegisterFee());
        vo.setOrderStatus(a.getOrderStatus());
        vo.setVisitStatus(a.getVisitStatus());
        vo.setPaymentOrderId(paymentOrderId);
        vo.setPaymentOrderNo(paymentOrderNo);
        vo.setCreateTime(a.getCreateTime());
        vo.setUpdateTime(a.getUpdateTime());
        return vo;
    }
}
