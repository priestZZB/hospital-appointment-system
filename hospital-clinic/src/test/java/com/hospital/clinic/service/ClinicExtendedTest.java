package com.hospital.clinic.service;

import com.hospital.clinic.entity.Appointment;
import com.hospital.clinic.entity.Checkin;
import com.hospital.clinic.entity.Department;
import com.hospital.clinic.entity.Schedule;
import com.hospital.clinic.mapper.AppointmentMapper;
import com.hospital.clinic.mapper.CheckinMapper;
import com.hospital.clinic.mapper.DepartmentMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.vo.CallMessageVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CheckinService + CallService + StopService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Clinic 扩展单元测试")
class ClinicExtendedTest {

    // ==================== CheckinService 测试 ====================

    @Nested
    @DisplayName("签到排队")
    class CheckinTest {

        @Mock
        private CheckinMapper checkinMapper;
        @Mock
        private AppointmentMapper appointmentMapper;
        @Mock
        private ScheduleMapper scheduleMapper;
        @Mock
        private DepartmentMapper departmentMapper;
        @Mock
        private StringRedisTemplate stringRedisTemplate;
        @Mock
        private ZSetOperations<String, String> zSetOperations;

        @InjectMocks
        private CheckinService checkinService;

        @BeforeEach
        void setUp() {
            lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        }

        @Test
        @DisplayName("签到成功加入排队队列")
        void shouldCheckinAndEnqueue() {
            // given
            Appointment appt = new Appointment();
            appt.setId(100L);
            appt.setPatientId(1L);
            appt.setOrderStatus("PAID");
            appt.setScheduleId(10L);
            appt.setDepartmentId(5L);
            appt.setDoctorId(3L);

            Schedule schedule = new Schedule();
            schedule.setScheduleDate(java.time.LocalDate.now());
            schedule.setPeriodStart(java.time.LocalTime.now().minusMinutes(10));

            when(appointmentMapper.selectById(100L)).thenReturn(appt);
            when(scheduleMapper.selectById(10L)).thenReturn(schedule);
            when(checkinMapper.selectByAppointmentId(100L)).thenReturn(null);
            when(checkinMapper.insert(any())).thenReturn(1);
            when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
            when(appointmentMapper.updateVisitStatus(anyLong(), anyString(), isNull())).thenReturn(1);

            // when
            com.hospital.clinic.dto.CheckinDTO dto = new com.hospital.clinic.dto.CheckinDTO();
            dto.setAppointmentId(100L);
            var result = checkinService.checkin(1L, dto);

            // then
            assertNotNull(result);
            assertEquals("WAITING", result.getQueueStatus());
            verify(zSetOperations).add(contains("queue:dept:5"), anyString(), anyDouble());
        }

        @Test
        @DisplayName("重复签到应抛异常")
        void shouldRejectDuplicateCheckin() {
            Appointment appt = new Appointment();
            appt.setId(100L);
            appt.setPatientId(1L);
            appt.setOrderStatus("PAID");
            appt.setScheduleId(10L);

            Schedule schedule = new Schedule();
            schedule.setScheduleDate(java.time.LocalDate.now());
            schedule.setPeriodStart(java.time.LocalTime.now().minusMinutes(10));

            Checkin existing = new Checkin();
            existing.setId(1L);

            when(appointmentMapper.selectById(100L)).thenReturn(appt);
            when(scheduleMapper.selectById(10L)).thenReturn(schedule);
            when(checkinMapper.selectByAppointmentId(100L)).thenReturn(existing);

            com.hospital.clinic.dto.CheckinDTO dto = new com.hospital.clinic.dto.CheckinDTO();
            dto.setAppointmentId(100L);

            assertThrows(BusinessException.class, () -> checkinService.checkin(1L, dto));
        }

        @Test
        @DisplayName("排队状态查询返回前面人数")
        void shouldReturnQueueStatus() {
            Checkin checkin = new Checkin();
            checkin.setId(1L);
            checkin.setDepartmentId(5L);
            checkin.setQueueStatus("WAITING");

            Department dept = new Department();
            dept.setDeptName("骨科");

            when(checkinMapper.selectById(1L)).thenReturn(checkin);
            when(zSetOperations.rank("queue:dept:5", "1")).thenReturn(3L);
            when(zSetOperations.size("queue:dept:5")).thenReturn(10L);
            when(departmentMapper.selectById(5L)).thenReturn(dept);

            var result = checkinService.getQueueStatus(1L);

            assertNotNull(result);
            assertEquals("骨科", result.getDeptName());
            assertEquals(3L, result.getAheadCount());
            assertEquals(10L, result.getTotalWaiting());
        }
    }

    // ==================== CallService 测试 ====================

    @Nested
    @DisplayName("叫号推送")
    class CallTest {

        @Mock
        private CheckinMapper checkinMapper;
        @Mock
        private DoctorMapper doctorMapper;
        @Mock
        private DepartmentMapper departmentMapper;
        @Mock
        private AppointmentMapper appointmentMapper;
        @Mock
        private SimpMessagingTemplate messagingTemplate;
        @Mock
        private StringRedisTemplate stringRedisTemplate;
        @Mock
        private ZSetOperations<String, String> zSetOperations;

        @InjectMocks
        private CallService callService;

        @BeforeEach
        void setUp() {
            lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        }

        @Test
        @DisplayName("叫号 ZPOPMIN 取最早签到并 WebSocket 广播")
        void shouldPopAndBroadcast() {
            Checkin checkin = new Checkin();
            checkin.setId(1L);
            checkin.setPatientId(100L);
            checkin.setDepartmentId(5L);
            checkin.setDoctorId(3L);
            checkin.setAppointmentId(10L);
            checkin.setCallCount(0);

            Department dept = new Department();
            dept.setDeptName("骨科");

            com.hospital.clinic.entity.Doctor doctor = new com.hospital.clinic.entity.Doctor();
            doctor.setName("李医生");

            Set<String> poppedSet = new HashSet<>();
            poppedSet.add("1");

            when(zSetOperations.popMin("queue:dept:5", 1)).thenReturn(poppedSet);
            when(checkinMapper.selectById(1L)).thenReturn(checkin);
            when(checkinMapper.updateCallInfo(eq(1L), eq("CALLED"), eq(1), eq("诊室1"))).thenReturn(1);
            when(appointmentMapper.updateVisitStatus(eq(10L), eq("CALLED"), isNull())).thenReturn(1);
            when(departmentMapper.selectById(5L)).thenReturn(dept);
            when(doctorMapper.selectById(3L)).thenReturn(doctor);

            var result = callService.callNext(5L, "诊室1", 3L);

            assertNotNull(result);
            assertEquals("CALL_NUMBER", result.getType());
            assertEquals("骨科", result.getDeptName());
            verify(messagingTemplate).convertAndSend(eq("/topic/call/5"), any(CallMessageVO.class));
        }

        @Test
        @DisplayName("队列为空时抛异常")
        void shouldThrowWhenQueueEmpty() {
            when(zSetOperations.popMin("queue:dept:5", 1)).thenReturn(Collections.emptySet());

            assertThrows(BusinessException.class, () -> callService.callNext(5L, "诊室1", 3L));
        }
    }

    // ==================== StopService 测试 ====================

    @Nested
    @DisplayName("停诊冲突校验")
    class StopTest {

        @Mock
        private StopApplicationMapper stopApplicationMapper;
        @Mock
        private ScheduleMapper scheduleMapper;
        @Mock
        private AppointmentMapper appointmentMapper;
        @Mock
        private AppointmentService appointmentService;
        @Mock
        private com.hospital.common.feign.PaymentFeignClient paymentFeignClient;

        @InjectMocks
        private StopService stopService;

        @Test
        @DisplayName("排班超过48小时应拒绝")
        void shouldRejectBeyond48Hours() {
            Schedule schedule = new Schedule();
            schedule.setId(10L);
            schedule.setScheduleDate(java.time.LocalDate.now().plusDays(3));
            schedule.setPeriodStart(java.time.LocalTime.of(8, 0));

            when(scheduleMapper.selectById(10L)).thenReturn(schedule);

            assertThrows(BusinessException.class, () -> stopService.apply(3L, 10L, "个人原因"));
        }

        @Test
        @DisplayName("存在已签到预约时应拒绝停诊")
        void shouldRejectWhenHasCheckin() {
            Schedule schedule = new Schedule();
            schedule.setId(10L);
            schedule.setScheduleDate(java.time.LocalDate.now().plusDays(1));
            schedule.setPeriodStart(java.time.LocalTime.of(8, 0));

            Appointment appt = new Appointment();
            appt.setId(1L);
            appt.setVisitStatus("CHECKED_IN");

            when(scheduleMapper.selectById(10L)).thenReturn(schedule);
            when(appointmentMapper.selectByScheduleId(10L)).thenReturn(java.util.List.of(appt));

            assertThrows(BusinessException.class, () -> stopService.apply(3L, 10L, "个人原因"));
        }

        @Test
        @DisplayName("正常停诊申请成功")
        void shouldApplySuccessfully() {
            Schedule schedule = new Schedule();
            schedule.setId(10L);
            schedule.setScheduleDate(java.time.LocalDate.now().plusDays(1));
            schedule.setPeriodStart(java.time.LocalTime.of(8, 0));

            when(scheduleMapper.selectById(10L)).thenReturn(schedule);
            when(appointmentMapper.selectByScheduleId(10L)).thenReturn(Collections.emptyList());
            when(stopApplicationMapper.selectByScheduleId(10L)).thenReturn(null);
            when(stopApplicationMapper.insert(any())).thenReturn(1);

            var result = stopService.apply(3L, 10L, "个人原因");

            assertNotNull(result);
            assertEquals("PENDING", result.getStatus());
            assertEquals(3L, result.getDoctorId());
        }
    }
}
