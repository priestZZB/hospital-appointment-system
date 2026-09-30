package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检查预约表实体（D1 检查预约与登记报到）
 * <p>
 * 影像类检查（exam_item.modality 非空）缴费后可预约检查时段，到院后登记报到。
 *
 * @see V9__lis_pacs.sql — exam_reservation 表 DDL
 */
@Data
public class ExamReservation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 exam_application.id */
    private Long applicationId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 预约检查日期 */
    private LocalDate reserveDate;

    /** 预约时段（如 08:30-09:00） */
    private String timeSlot;

    /** 检查室 */
    private String room;

    /** 状态: BOOKED-已约 / CHECKED_IN-已报到 / DONE-已完成 / CANCELLED-已取消 */
    private String status;

    /** 报到时间 */
    private LocalDateTime checkinTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
