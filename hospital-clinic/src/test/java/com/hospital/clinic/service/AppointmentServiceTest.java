package com.hospital.clinic.service;

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
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.feign.PaymentFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 挂号下单核心业务单元测试
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentMapper appointmentMapper;
    @Mock private SlotMapper slotMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private DoctorMapper doctorMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private SlotService slotService;
    @Mock private PatientFeignClient patientFeignClient;
    @Mock private PaymentFeignClient paymentFeignClient;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AppointmentService appointmentService;

    private AppointmentSubmitDTO validDTO;
    private Slot availableSlot;
    private Schedule activeSchedule;
    private Doctor doctor;
    private Department dept;
    private Map<String, Object> patientInfo;

    @BeforeEach
    void setUp() {
        validDTO = new AppointmentSubmitDTO();
        validDTO.setSlotId(1L);
        validDTO.setScheduleId(10L);

        availableSlot = new Slot();
        availableSlot.setId(1L);
        availableSlot.setScheduleId(10L);
        availableSlot.setSlotSeq(1);
        availableSlot.setSlotStart(LocalTime.of(9, 0));
        availableSlot.setSlotEnd(LocalTime.of(9, 10));
        availableSlot.setStatus("AVAILABLE");
        availableSlot.setVersion(0);

        activeSchedule = new Schedule();
        activeSchedule.setId(10L);
        activeSchedule.setDoctorId(100L);
        activeSchedule.setDepartmentId(200L);
        activeSchedule.setScheduleDate(LocalDate.now().plusDays(1));
        activeSchedule.setPeriod("AM");
        activeSchedule.setPeriodStart(LocalTime.of(8, 0));
        activeSchedule.setPeriodEnd(LocalTime.of(12, 0));
        activeSchedule.setTotalSlots(24);
        activeSchedule.setSlotDuration(10);
        activeSchedule.setRegisterFee(new BigDecimal("15.00"));
        activeSchedule.setStatus(1);

        doctor = new Doctor();
        doctor.setId(100L);
        doctor.setName("测试医生");
        doctor.setTitle("CHIEF");

        dept = new Department();
        dept.setId(200L);
        dept.setDeptName("内科");

        patientInfo = Map.of("id", 50L, "userId", 5L, "verifyStatus", 1, "name", "张三");
    }

    // ==================== 正常挂号成功 ====================

    @Test
    void testSubmit_Success() throws InterruptedException {
        Long userId = 5L;

        when(patientFeignClient.getByUserId(userId)).thenReturn(patientInfo);
        when(slotMapper.selectById(1L)).thenReturn(availableSlot);
        when(scheduleMapper.selectById(10L)).thenReturn(activeSchedule);
        when(stringRedisTemplate.hasKey(contains("repeat:appointment:"))).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(slotService.deductSlot(1L, 0)).thenReturn(true);
        when(doctorMapper.selectById(100L)).thenReturn(doctor);
        when(departmentMapper.selectById(200L)).thenReturn(dept);
        doAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(1L);
            return 1;
        }).when(appointmentMapper).insert(any(Appointment.class));
        when(paymentFeignClient.createOrder(anyMap())).thenReturn(
                Map.of("id", 500L, "orderNo", "PAY20260720001"));

        AppointmentVO result = appointmentService.submit(userId, validDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("PENDING_PAY", result.getOrderStatus());
        assertEquals(new BigDecimal("15.00"), result.getRegisterFee());
        assertEquals("测试医生", result.getDoctorName());
        assertEquals("内科", result.getDepartmentName());

        verify(slotService, times(1)).deductSlot(1L, 0);
        verify(appointmentMapper, times(1)).insert(any(Appointment.class));
        verify(paymentFeignClient, times(1)).createOrder(anyMap());
    }

    // ==================== 并发号源不足 ====================

    @Test
    void testSubmit_SlotNotAvailable() {
        Long userId = 5L;

        when(patientFeignClient.getByUserId(userId)).thenReturn(patientInfo);
        availableSlot.setStatus("BOOKED");
        when(slotMapper.selectById(1L)).thenReturn(availableSlot);

        assertThrows(BusinessException.class, () -> appointmentService.submit(userId, validDTO));
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    // ==================== 乐观锁冲突（并发抢号） ====================

    @Test
    void testSubmit_ConcurrentSoldOut() throws InterruptedException {
        Long userId = 5L;

        when(patientFeignClient.getByUserId(userId)).thenReturn(patientInfo);
        when(slotMapper.selectById(1L)).thenReturn(availableSlot);
        when(scheduleMapper.selectById(10L)).thenReturn(activeSchedule);
        when(stringRedisTemplate.hasKey(contains("repeat:appointment:"))).thenReturn(false);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(slotService.deductSlot(1L, 0)).thenReturn(false);

        assertThrows(BusinessException.class, () -> appointmentService.submit(userId, validDTO));
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    // ==================== 同一患者重复挂号 ====================

    @Test
    void testSubmit_DuplicateAppointment() {
        Long userId = 5L;

        when(patientFeignClient.getByUserId(userId)).thenReturn(patientInfo);
        when(slotMapper.selectById(1L)).thenReturn(availableSlot);
        when(scheduleMapper.selectById(10L)).thenReturn(activeSchedule);
        when(stringRedisTemplate.hasKey(contains("repeat:appointment:"))).thenReturn(true);

        assertThrows(BusinessException.class, () -> appointmentService.submit(userId, validDTO));
        verify(slotService, never()).deductSlot(anyLong(), anyInt());
    }

    // ==================== slotId/scheduleId 不一致 ====================

    @Test
    void testSubmit_SlotScheduleMismatch() {
        Long userId = 5L;

        when(patientFeignClient.getByUserId(userId)).thenReturn(patientInfo);
        // slot 的 scheduleId=999 与 DTO 的 scheduleId=10 不匹配
        availableSlot.setScheduleId(999L);
        when(slotMapper.selectById(1L)).thenReturn(availableSlot);
        when(scheduleMapper.selectById(10L)).thenReturn(activeSchedule);

        assertThrows(BusinessException.class, () -> appointmentService.submit(userId, validDTO));
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    // ==================== 取消预约（PENDING_PAY 直接释放号源） ====================

    @Test
    void testCancel_Success() {
        Long appointmentId = 1L;
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setPatientId(50L);
        appointment.setSlotId(1L);
        appointment.setScheduleId(10L);
        appointment.setOrderStatus("PENDING_PAY");

        when(appointmentMapper.selectById(appointmentId)).thenReturn(appointment);
        when(appointmentMapper.cancel(anyLong(), anyString(), anyString())).thenReturn(1);

        appointmentService.cancel(appointmentId, "行程变动");

        verify(appointmentMapper, times(1)).cancel(eq(appointmentId), eq("CANCELLED"), eq("行程变动"));
        verify(slotService, times(1)).releaseSlot(1L);
    }

    // ==================== 取消预约（已取消状态幂等拒绝） ====================

    @Test
    void testCancel_AlreadyCancelled() {
        Long appointmentId = 1L;
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setOrderStatus("CANCELLED");

        when(appointmentMapper.selectById(appointmentId)).thenReturn(appointment);

        assertThrows(BusinessException.class, () -> appointmentService.cancel(appointmentId, "重复取消"));
        verify(appointmentMapper, never()).cancel(anyLong(), anyString(), anyString());
    }

    // ==================== 取消预约（SQL 状态守卫返回 0） ====================

    @Test
    void testCancel_SqlGuardReturnsZero() {
        Long appointmentId = 1L;
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setPatientId(50L);
        appointment.setSlotId(1L);
        appointment.setScheduleId(10L);
        appointment.setOrderStatus("PENDING_PAY");

        when(appointmentMapper.selectById(appointmentId)).thenReturn(appointment);
        // SQL 守卫返回 0（状态已被其他线程变更）
        when(appointmentMapper.cancel(anyLong(), anyString(), anyString())).thenReturn(0);

        assertThrows(BusinessException.class, () -> appointmentService.cancel(appointmentId, "行程变动"));
    }

    // ==================== 患者未实名 ====================

    @Test
    void testSubmit_PatientNotVerified() {
        Long userId = 6L;
        Map<String, Object> unverifiedInfo = Map.of("id", 51L, "userId", userId, "verifyStatus", 0, "name", "李四");

        when(patientFeignClient.getByUserId(userId)).thenReturn(unverifiedInfo);

        assertThrows(BusinessException.class, () -> appointmentService.submit(userId, validDTO));
        verify(slotMapper, never()).selectById(anyLong());
    }
}
