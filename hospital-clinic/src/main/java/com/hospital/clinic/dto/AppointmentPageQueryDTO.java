package com.hospital.clinic.dto;

import com.hospital.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理端预约分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppointmentPageQueryDTO extends PageDTO {

    /** 科室 ID */
    private Long departmentId;

    /** 医生 ID */
    private Long doctorId;

    /** 患者 ID */
    private Long patientId;

    /** 订单状态：PENDING_PAY/PAID/CANCELLED/TIMEOUT/REFUNDED */
    private String orderStatus;

    /** 就诊日期（yyyy-MM-dd） */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;
}
