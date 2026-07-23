package com.hospital.payment.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.feign.AppointmentFeignClient;
import com.hospital.payment.entity.LocalMessage;
import com.hospital.payment.entity.PaymentOrder;
import com.hospital.payment.mapper.LocalMessageMapper;
import com.hospital.payment.mapper.PaymentOrderMapper;
import com.hospital.payment.mapper.RefundRecordMapper;
import com.hospital.payment.vo.PaymentOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付订单服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentOrderMapper orderMapper;
    @Mock private LocalMessageMapper messageMapper;
    @Mock private RefundRecordMapper refundRecordMapper;
    @Mock private AppointmentFeignClient appointmentFeignClient;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentOrder pendingOrder;
    private PaymentOrder paidOrder;
    private Map<String, Object> createOrderDTO;

    @BeforeEach
    void setUp() {
        pendingOrder = new PaymentOrder();
        pendingOrder.setId(1L);
        pendingOrder.setOrderNo("PAY202607200001");
        pendingOrder.setAppointmentId(100L);
        pendingOrder.setPatientId(50L);
        pendingOrder.setAmount(new BigDecimal("15.00"));
        pendingOrder.setOrderType("REGISTRATION");
        pendingOrder.setStatus("PENDING");
        pendingOrder.setExpireTime(LocalDateTime.now().plusMinutes(30));
        pendingOrder.setVersion(0);

        paidOrder = new PaymentOrder();
        paidOrder.setId(2L);
        paidOrder.setOrderNo("PAY202607200002");
        paidOrder.setAppointmentId(101L);
        paidOrder.setPatientId(51L);
        paidOrder.setAmount(new BigDecimal("15.00"));
        paidOrder.setOrderType("REGISTRATION");
        paidOrder.setStatus("PAID");
        paidOrder.setVersion(1);

        createOrderDTO = Map.of(
                "appointmentId", 100L,
                "patientId", 50L,
                "amount", new BigDecimal("15.00"),
                "orderType", "REGISTRATION"
        );
    }

    // ==================== 创建订单 ====================

    @Test
    void testCreateOrder_Success() {
        doAnswer(inv -> {
            PaymentOrder o = inv.getArgument(0);
            o.setId(1L);
            return 1;
        }).when(orderMapper).insert(any(PaymentOrder.class));
        doAnswer(inv -> {
            LocalMessage m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        }).when(messageMapper).insert(any(LocalMessage.class));

        Map<String, Object> result = paymentService.createOrder(createOrderDTO);

        assertNotNull(result);
        assertEquals(1L, ((Number) result.get("id")).longValue());
        assertEquals("PENDING", result.get("status"));
        verify(orderMapper, times(1)).insert(any(PaymentOrder.class));
        verify(messageMapper, times(1)).insert(any(LocalMessage.class));
    }

    // ==================== 模拟支付成功 ====================

    @Test
    void testProcessPayment_Success() {
        when(orderMapper.selectById(1L)).thenReturn(pendingOrder);
        when(orderMapper.markAsPaid(eq(1L), eq("SIMULATED"), eq(0))).thenReturn(1);

        // 支付成功后再次查询
        PaymentOrder paidOrder = new PaymentOrder();
        paidOrder.setId(1L);
        paidOrder.setOrderNo("PAY202607200001");
        paidOrder.setAppointmentId(100L);
        paidOrder.setPatientId(50L);
        paidOrder.setAmount(new BigDecimal("15.00"));
        paidOrder.setOrderType("REGISTRATION");
        paidOrder.setStatus("PAID");
        paidOrder.setPayTime(LocalDateTime.now());
        paidOrder.setPayMethod("SIMULATED");
        paidOrder.setExpireTime(pendingOrder.getExpireTime());
        paidOrder.setVersion(1);

        when(orderMapper.selectById(1L)).thenReturn(pendingOrder, paidOrder);

        PaymentOrderVO result = paymentService.processPayment(1L);

        assertNotNull(result);
        assertEquals("PAID", result.getStatus());
        verify(orderMapper, times(1)).markAsPaid(eq(1L), eq("SIMULATED"), eq(0));
    }

    // ==================== 重复支付拒绝 ====================

    @Test
    void testProcessPayment_AlreadyPaid() {
        when(orderMapper.selectById(2L)).thenReturn(paidOrder);

        assertThrows(BusinessException.class, () -> paymentService.processPayment(2L));
        verify(orderMapper, never()).markAsPaid(anyLong(), anyString(), anyInt());
    }

    // ==================== 超时关单（幂等） ====================

    @Test
    void testCloseTimeoutOrder_Success() {
        when(orderMapper.selectByOrderNo("PAY202607200001")).thenReturn(pendingOrder);
        when(orderMapper.updateStatusWithVersion(eq(1L), eq("TIMEOUT"), eq(0))).thenReturn(1);

        paymentService.closeTimeoutOrder("PAY202607200001");

        verify(orderMapper, times(1)).updateStatusWithVersion(eq(1L), eq("TIMEOUT"), eq(0));
    }

    @Test
    void testCloseTimeoutOrder_AlreadyPaid_Idempotent() {
        when(orderMapper.selectByOrderNo("PAY202607200002")).thenReturn(paidOrder);

        paymentService.closeTimeoutOrder("PAY202607200002");

        verify(orderMapper, never()).updateStatusWithVersion(anyLong(), anyString(), anyInt());
    }
}
