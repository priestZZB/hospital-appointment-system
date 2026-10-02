package com.hospital.clinic.service;

import cn.hutool.core.lang.UUID;
import com.hospital.clinic.constant.ConsultFeePolicy;
import com.hospital.clinic.dto.AppointmentSubmitDTO;
import com.hospital.clinic.dto.AppointmentPageQueryDTO;
import com.hospital.clinic.dto.RescheduleDTO;
import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.entity.Slot;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.CheckinMapper;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.mapper.SlotMapper;
import com.hospital.clinic.vo.AppointmentVO;
import com.hospital.clinic.vo.QueuePatientVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.PaymentFeignClient;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final CheckinMapper checkinMapper;
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
        // 校验排班已通过门诊部确认（真实业务：仅已确认排班生成号源供挂号）
        if (!"CONFIRMED".equals(schedule.getAuditStatus())) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE, "该排班尚未确认，暂不可挂号");
        }
        // 校验 slot 与 schedule 的关联关系
        if (!slot.getScheduleId().equals(scheduleId)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "号源与排班信息不一致");
        }

        // ========== 第2.5步：绿色通道校验（迭代9 A4） ==========
        // 挂号请求可带 channelType：GREEN 时要求号源为绿色通道号源；
        // 不传或 NORMAL 保持既有行为，向后兼容。
        validateChannelType(dto.getChannelType(), slot.getChannelType());

        // ========== 第3步：Redisson 分布式锁 + 乐观锁扣减（锁内包含防重复校验 + 设置防重复键） ==========
        // 锁粒度 = 同一患者 + 同一排班：串行化同一患者的并发挂号，杜绝跨号源重复预约；
        // 不同患者抢同一号源由 slot 乐观锁（version）兜底。
        String repeatKey = REPEAT_KEY_PREFIX + patientId + ":" + scheduleId;
        String lockKey = LOCK_KEY_PREFIX + patientId + ":" + scheduleId;
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

            // 扣减成功后立即在锁内设置防重复键，关闭并发窗口（支付失败时在下方 catch 中清理）
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

        // ========== 第4步：创建预约记录 ==========
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Department dept = departmentMapper.selectById(schedule.getDepartmentId());

        // 分层定价（迭代9 A5）：feeType=EXPERT 时按医生职称取档位价，普通号沿用排班登记价
        BigDecimal registerFee = ConsultFeePolicy.resolveFee(
                schedule.getFeeType(), doctor != null ? doctor.getTitle() : null, schedule.getRegisterFee());

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
        appointment.setRegisterFee(registerFee);
        appointment.setOrderStatus("PENDING_PAY");
        appointment.setVisitStatus(null);
        appointment.setOverbookFlag(0);
        int revisit = 0;
        try {
            LocalDate since = LocalDate.now().minusDays(30);
            long completed = appointmentMapper.countCompletedSince(patientId, since);
            revisit = completed > 0 ? 1 : 0;
        } catch (Exception e) {
            log.warn("[挂号] 复诊识别异常，按初诊处理: patientId={}", patientId, e);
        }
        appointment.setIsRevisit(revisit);
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
            orderDTO.put("amount", appointment.getRegisterFee());
            orderDTO.put("orderType", "REGISTRATION");
            Map<String, Object> payResult = paymentFeignClient.createOrder(orderDTO);
            if (payResult != null) {
                paymentOrderId = toLong(payResult.get("id"));
                paymentOrderNo = (String) payResult.get("orderNo");
            }
        } catch (Exception e) {
            // 支付订单创建失败：直接抛异常，@Transactional 会回滚预约记录和号源扣减
            // 清理锁内已设置的防重复键，避免用户被误锁
            stringRedisTemplate.delete(repeatKey);
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

            // 校验取消时间：就诊时段已开始则不可取消
            Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
            Slot apptSlot = slotMapper.selectById(appointment.getSlotId());
            if (schedule != null && apptSlot != null) {
                LocalDateTime slotStart = LocalDateTime.of(schedule.getScheduleDate(), apptSlot.getSlotStart());
                if (LocalDateTime.now().isAfter(slotStart)) {
                    throw new BusinessException(ErrorCodeEnum.APPOINTMENT_CANNOT_CANCEL);
                }
            }

            // 取消预约（传入预期状态，防止竞态覆盖）
            int rows = appointmentMapper.cancel(appointmentId, "CANCELLED", reason, status);
            if (rows == 0) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_CANNOT_CANCEL, "该预约状态已变更，请刷新重试");
            }
            log.info("[挂号] 预约已取消: appointmentId={}, reason={}", appointmentId, reason);

            // PENDING_PAY：读取号源版本号后乐观锁释放
            // PAID：由 payment-service 退款回调统一释放号源
            // 加号预约（overbook_flag=1，迭代9 A2）共享已被占用的号源，
            // 取消/超时/退款时不得释放该号源，否则会误放他人预约
            if ("PENDING_PAY".equals(status) && !isOverbook(appointment)) {
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
     * 医生加号（迭代9 A2）
     * <p>
     * 号源已被约满（BOOKED）且排班开启加号开关（schedule.overbook=1）时，
     * 允许在同名源上追加挂号：不扣减号源（保持 BOOKED，同一号源多预约共享），
     * 创建带 overbook_flag=1 的预约并生成支付订单；就诊顺序按加号标记排在正常预约之后。
     * 防重复与并发：与普通挂号共用「患者+排班」防重复键与分布式锁。
     *
     * @param userId 用户 ID（加号对象为当前登录用户对应的患者）
     * @param dto    与既有挂号请求体一致（slotId + scheduleId）
     */
    @Transactional(rollbackFor = Exception.class)
    public AppointmentVO overbook(Long userId, AppointmentSubmitDTO dto) {
        Long slotId = dto.getSlotId();
        Long scheduleId = dto.getScheduleId();

        // ========== 1. Feign 解析患者（与 submit 一致） ==========
        Long patientId;
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty() || patientInfo.get("id") == null) {
                throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED, "患者档案不存在");
            }
            patientId = toLong(patientInfo.get("id"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[加号] 远程调用 patient-service 失败: userId={}", userId, e);
            throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR, "患者信息服务不可用");
        }

        // ========== 2. 校验号源已约满 + 排班开启加号 ==========
        Slot slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE, "号源不存在");
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE);
        }
        if (!"CONFIRMED".equals(schedule.getAuditStatus())) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE, "该排班尚未确认，暂不可挂号");
        }
        if (!slot.getScheduleId().equals(scheduleId)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "号源与排班信息不一致");
        }
        if (!"BOOKED".equals(slot.getStatus())) {
            // 加号前提：号源已被正常预约占满；AVAILABLE 的号源请走普通挂号
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已被约满（BOOKED）的号源可加号");
        }
        if (schedule.getOverbook() == null || schedule.getOverbook() != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该排班未开启加号，请联系门诊部管理员");
        }

        // ========== 3. 分布式锁内防重复 + 创建加号预约 ==========
        String repeatKey = REPEAT_KEY_PREFIX + patientId + ":" + scheduleId;
        String lockKey = LOCK_KEY_PREFIX + patientId + ":" + scheduleId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "系统繁忙，请稍后重试");
            }
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(repeatKey))) {
                throw new BusinessException(ErrorCodeEnum.DUPLICATE_APPOINTMENT);
            }

            Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
            Department dept = departmentMapper.selectById(schedule.getDepartmentId());

            // 加号同样适用分层定价（专家号按职称档位价）
            BigDecimal registerFee = ConsultFeePolicy.resolveFee(
                    schedule.getFeeType(), doctor != null ? doctor.getTitle() : null, schedule.getRegisterFee());

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
            appointment.setRegisterFee(registerFee);
            appointment.setOrderStatus("PENDING_PAY");
            appointment.setVisitStatus(null);
            appointment.setOverbookFlag(1);
            appointment.setIsRevisit(0);
            appointmentMapper.insert(appointment);
            log.info("[加号] 加号预约已创建: appointmentId={}, slotId={}, patientId={}",
                    appointment.getId(), slotId, patientId);

            // 成功后设置防重复键（与普通挂号语义一致）
            long ttlSeconds = calculateTTL(schedule.getScheduleDate(), slot.getSlotStart());
            if (ttlSeconds > 0) {
                stringRedisTemplate.opsForValue().set(repeatKey, "1", Duration.ofSeconds(ttlSeconds));
            }

            // ========== 4. 支付订单（失败回滚预约） ==========
            Long paymentOrderId = null;
            String paymentOrderNo = null;
            try {
                Map<String, Object> orderDTO = new HashMap<>();
                orderDTO.put("appointmentId", appointment.getId());
                orderDTO.put("patientId", patientId);
                orderDTO.put("amount", appointment.getRegisterFee());
                orderDTO.put("orderType", "REGISTRATION");
                Map<String, Object> payResult = paymentFeignClient.createOrder(orderDTO);
                if (payResult != null) {
                    paymentOrderId = toLong(payResult.get("id"));
                    paymentOrderNo = (String) payResult.get("orderNo");
                }
            } catch (Exception e) {
                stringRedisTemplate.delete(repeatKey);
                log.error("[加号] 创建支付订单失败，事务回滚: appointmentId={}", appointment.getId(), e);
                throw new BusinessException(ErrorCodeEnum.REMOTE_SERVICE_ERROR, "支付服务暂不可用，请稍后重试");
            }

            return buildVO(appointment, doctor, dept, slot, paymentOrderId, paymentOrderNo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "加号操作被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 退号改期（迭代9 A3）
     * <p>
     * 未就诊/未取消的预约改期到同科室另一号源。事务内：
     * 新号源 AVAILABLE→BOOKED（乐观锁扣减）→ 旧号源 BOOKED→AVAILABLE（乐观锁释放，
     * 失败抛异常整体回滚）→ 更新预约 slotId/scheduleId 及冗余日期/时段/号序。
     * 原挂号费与支付订单保持不变（同科室费用不重复收取）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AppointmentVO reschedule(Long appointmentId, Long userId, RescheduleDTO dto) {
        String lockKey = APPOINTMENT_LOCK_KEY_PREFIX + appointmentId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "系统繁忙，请稍后重试");
            }

            Appointment appointment = appointmentMapper.selectById(appointmentId);
            if (appointment == null) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_NOT_FOUND);
            }
            // 权限：管理员/医生可代办改期，患者仅可改本人预约
            if (!UserContext.isDoctorOrAdmin()) {
                Long patientId = resolvePatientId(userId);
                if (patientId == null || !patientId.equals(appointment.getPatientId())) {
                    throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权操作他人预约");
                }
            }

            // 状态校验：未就诊/未取消（仅 PENDING_PAY / PAID 且尚未签到就诊）
            String orderStatus = appointment.getOrderStatus();
            if (!"PENDING_PAY".equals(orderStatus) && !"PAID".equals(orderStatus)) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_CANNOT_CANCEL,
                        "该预约已取消/退款/超时，不可改期");
            }
            if (appointment.getVisitStatus() != null) {
                throw new BusinessException(ErrorCodeEnum.APPOINTMENT_CANNOT_CANCEL,
                        "该预约已签到或就诊中，不可改期");
            }

            // 原排班与新排班必须同科室
            Schedule oldSchedule = scheduleMapper.selectById(appointment.getScheduleId());
            Schedule newSchedule = scheduleMapper.selectById(dto.getNewScheduleId());
            if (oldSchedule == null) {
                throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
            }
            if (newSchedule == null || newSchedule.getStatus() != 1) {
                throw new BusinessException(ErrorCodeEnum.SCHEDULE_NOT_FOUND);
            }
            if (!"CONFIRMED".equals(newSchedule.getAuditStatus())) {
                throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE, "新排班尚未确认，暂不可挂号");
            }
            if (!oldSchedule.getDepartmentId().equals(newSchedule.getDepartmentId())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅支持同科室改期");
            }
            if (dto.getNewSlotId().equals(appointment.getSlotId())
                    && dto.getNewScheduleId().equals(appointment.getScheduleId())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "新号源与原号源相同，无需改期");
            }

            // 新号源校验
            Slot newSlot = slotMapper.selectById(dto.getNewSlotId());
            if (newSlot == null || !"AVAILABLE".equals(newSlot.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE, "新号源不可用");
            }
            if (!newSlot.getScheduleId().equals(dto.getNewScheduleId())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "号源与排班信息不一致");
            }

            // 事务内：新号源扣减 → 旧号源释放 → 预约更新
            boolean deducted = slotService.deductSlot(newSlot.getId(), newSlot.getVersion());
            if (!deducted) {
                throw new BusinessException(ErrorCodeEnum.SLOT_NOT_ENOUGH);
            }

            Slot oldSlot = slotMapper.selectById(appointment.getSlotId());
            if (oldSlot != null && "BOOKED".equals(oldSlot.getStatus())) {
                int released = slotMapper.releaseSlot(oldSlot.getId(), oldSlot.getVersion());
                if (released == 0) {
                    // 释放失败（版本冲突）→ 抛异常整体回滚（新号源扣减一并回滚）
                    throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "原号源释放失败，请稍后重试");
                }
            }

            appointmentMapper.updateReschedule(appointmentId,
                    dto.getNewSlotId(), dto.getNewScheduleId(),
                    newSchedule.getScheduleDate(), newSchedule.getPeriod(), newSlot.getSlotSeq());
            log.info("[改期] 预约已改期: appointmentId={}, 旧slot={}, 新slot={}",
                    appointmentId, appointment.getSlotId(), dto.getNewSlotId());

            // 清理旧排班防重复键，允许患者后续再挂原排班
            stringRedisTemplate.delete(REPEAT_KEY_PREFIX
                    + appointment.getPatientId() + ":" + appointment.getScheduleId());

            return appointmentMapper.selectByIdWithDetail(appointmentId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "改期操作被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 我的预约列表（联表查询，避免 N+1） */
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
    public AppointmentVO getById(Long appointmentId, Long userId) {
        AppointmentVO vo = appointmentMapper.selectByIdWithDetail(appointmentId);
        if (vo == null) {
            throw new BusinessException(ErrorCodeEnum.APPOINTMENT_NOT_FOUND);
        }
        // 权限：管理员/医生可查看任意预约，患者仅可查看本人预约
        if (!UserContext.isDoctorOrAdmin()) {
            Long patientId = resolvePatientId(userId);
            if (patientId == null || !patientId.equals(vo.getPatientId())) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权查看他人预约");
            }
        }
        return vo;
    }

    /**
     * 管理端分页查询预约（全量 + 多条件筛选）
     */
    public Map<String, Object> pageForAdmin(AppointmentPageQueryDTO dto) {
        List<AppointmentVO> list = appointmentMapper.selectPageWithDetail(
                dto.getDepartmentId(), dto.getDoctorId(), dto.getPatientId(),
                dto.getOrderStatus(), dto.getAppointmentDate(),
                dto.getOffset(), dto.getPageSize());
        long total = appointmentMapper.countPageWithDetail(
                dto.getDepartmentId(), dto.getDoctorId(), dto.getPatientId(),
                dto.getOrderStatus(), dto.getAppointmentDate());
        fillAppointmentPatientNames(list);

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("pageNo", dto.getPageNo());
        result.put("pageSize", dto.getPageSize());
        return result;
    }

    /**
     * 医生工作台：今日待接诊/已叫号患者列表（科室维度，可选医生过滤）
     */
    public List<QueuePatientVO> todayQueue(Long departmentId, Long doctorId) {
        List<QueuePatientVO> list = checkinMapper.selectTodayQueueByDept(
                departmentId, doctorId,
                List.of("WAITING", "CALLED", "RE_CALLED", "IN_CONSULT", "MISSED"));
        fillQueuePatientNames(list);
        return list;
    }

    // ==================== 患者姓名批量补充（Feign 直连 patient-service） ====================

    private void fillAppointmentPatientNames(List<AppointmentVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> names = resolvePatientNames(
                list.stream().map(AppointmentVO::getPatientId).collect(Collectors.toSet()));
        list.forEach(vo -> vo.setPatientName(names.get(vo.getPatientId())));
    }

    private void fillQueuePatientNames(List<QueuePatientVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> names = resolvePatientNames(
                list.stream().map(QueuePatientVO::getPatientId).collect(Collectors.toSet()));
        list.forEach(vo -> vo.setPatientName(names.get(vo.getPatientId())));
    }

    private Map<Long, String> resolvePatientNames(Set<Long> patientIds) {
        Map<Long, String> names = new HashMap<>();
        if (patientIds == null || patientIds.isEmpty()) {
            return names;
        }
        try {
            List<Map<String, Object>> batch = patientFeignClient.getBatch(
                    patientIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toList()));
            if (batch != null) {
                for (Map<String, Object> info : batch) {
                    Object id = info.get("id");
                    Object name = info.get("name");
                    if (id != null && name != null) {
                        names.put(((Number) id).longValue(), String.valueOf(name));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[预约] 患者姓名批量查询失败（忽略）: {}", e.getMessage());
        }
        return names;
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
     * 退款后标记预约已退款（payment-service 退款回调）
     * <p>
     * 迭代17审计修复：PAID 预约退款（患者取消/停诊退款）后号源必须释放，
     * 否则 slot 永久停留 BOOKED 造成号源丢失。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsRefunded(Long appointmentId) {
        // 仅 PAID 可转为 REFUNDED
        int rows = appointmentMapper.updateOrderStatus(appointmentId, "REFUNDED", "PAID");
        if (rows == 0) {
            log.warn("[挂号] 标记退款失败（状态不匹配）: appointmentId={}", appointmentId);
            return;
        }
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment != null) {
            // 释放号源（乐观锁；加号预约共享号源跳过）+ 清除重复挂号键
            doReleaseSlot(appointment);
            String repeatKey = REPEAT_KEY_PREFIX + appointment.getPatientId() + ":" + appointment.getScheduleId();
            stringRedisTemplate.delete(repeatKey);
        }
        log.info("[挂号] 退款已标记: appointmentId={}", appointmentId);
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
        doReleaseSlot(appointment);
        String repeatKey = REPEAT_KEY_PREFIX + appointment.getPatientId() + ":" + appointment.getScheduleId();
        stringRedisTemplate.delete(repeatKey);
        log.info("[挂号] 超时关单，号源已释放: appointmentId={}", appointmentId);
    }

    /**
     * 号源释放公共实现：乐观锁释放；加号预约（overbook_flag=1，迭代9 A2）
     * 共享已被占用的号源，跳过释放防止误放他人预约。
     */
    private void doReleaseSlot(Appointment appointment) {
        if (isOverbook(appointment)) {
            return;
        }
        Slot slot = slotMapper.selectById(appointment.getSlotId());
        if (slot != null && "BOOKED".equals(slot.getStatus())) {
            slotService.releaseSlot(appointment.getSlotId(), slot.getVersion());
        }
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
     * 绿色通道校验（迭代9 A4）：
     * 请求带 channelType=GREEN 时，号源必须为绿色通道（channel_type=GREEN）；
     * 不传或 NORMAL 不限制（向后兼容）；非法取值直接拒绝。
     */
    private void validateChannelType(String requestedChannelType, String slotChannelType) {
        if (requestedChannelType == null || requestedChannelType.isBlank()
                || ConsultFeePolicy.FEE_TYPE_NORMAL.equals(requestedChannelType)) {
            return; // 未指定或普通通道：不限制号源（既有行为）
        }
        if (!"GREEN".equals(requestedChannelType)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "无效的号源通道类型: " + requestedChannelType);
        }
        if (!"GREEN".equals(slotChannelType)) {
            throw new BusinessException(ErrorCodeEnum.SLOT_NOT_AVAILABLE, "该号源不是绿色通道号源");
        }
    }

    /** 是否为加号预约（共享号源，取消/超时/退款时不得释放号源） */
    private boolean isOverbook(Appointment appointment) {
        return appointment.getOverbookFlag() != null && appointment.getOverbookFlag() == 1;
    }

    /**
     * 通过 userId 解析 patientId（auth userId ≠ patient db id）
     */
    private Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                return null;
            }
            return toLong(patientInfo.get("id"));
        } catch (Exception e) {
            log.warn("[挂号] 查询患者信息失败: userId={}", userId, e);
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
        vo.setOverbookFlag(a.getOverbookFlag());
        vo.setPaymentOrderId(paymentOrderId);
        vo.setPaymentOrderNo(paymentOrderNo);
        vo.setCreateTime(a.getCreateTime());
        vo.setUpdateTime(a.getUpdateTime());
        return vo;
    }
}

