package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.entity.ExamReservation;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ExamItemMapper;
import com.hospital.medsupply.mapper.ExamReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查预约服务（D1 检查预约与登记报到）
 * <p>
 * 影像类检查（exam_item.modality 非空）缴费后可预约检查时段，到院后登记报到；
 * 申请单执行登记仍走 {@link ExamService#executeExam}（既有流程），预约不联动申请状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamReservationService {

    private final ExamReservationMapper reservationMapper;
    private final ExamApplicationMapper applicationMapper;
    private final ExamItemMapper examItemMapper;

    /**
     * 预约检查：校验申请已缴费且为影像类项目 → 防重复预约 → 插入 BOOKED 记录
     * <p>
     * 毕设简化：同检查室同日期同时段允许多条预约，不做时段容量强校验。
     *
     * @param applicationId 检查申请 ID
     * @param reserveDate   预约日期
     * @param timeSlot      预约时段
     * @param room          检查室（可选）
     * @param operatorId    操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReservation book(Long applicationId, LocalDate reserveDate, String timeSlot,
                                String room, Long operatorId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "applicationId 不能为空");
        }
        if (reserveDate == null || timeSlot == null || timeSlot.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "reserveDate、timeSlot 不能为空");
        }
        if (reserveDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "预约日期不能早于今天");
        }

        // 申请必须存在且已缴费
        ExamApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.EXAM_APPLICATION_NOT_FOUND);
        }
        if (!"PAID".equals(application.getPayStatus())) {
            throw new BusinessException(ErrorCodeEnum.PAY_NOT_COMPLETED, "检查尚未缴费，不可预约");
        }

        // 仅影像类项目可预约（exam_item.modality 非空，D5 扩列）
        ExamItem item = application.getExamItemId() == null ? null
                : examItemMapper.selectById(application.getExamItemId());
        if (item == null || item.getModality() == null || item.getModality().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该检查项目非影像类项目，无需预约检查时段");
        }

        // 同一申请存在未取消的预约时拒绝重复预约
        List<ExamReservation> existing = reservationMapper.selectByApplication(applicationId);
        boolean activeExists = existing.stream().anyMatch(r -> !"CANCELLED".equals(r.getStatus()));
        if (activeExists) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "该检查申请已预约，请勿重复预约");
        }

        ExamReservation reservation = new ExamReservation();
        reservation.setApplicationId(applicationId);
        reservation.setPatientId(application.getPatientId());
        reservation.setReserveDate(reserveDate);
        reservation.setTimeSlot(timeSlot);
        reservation.setRoom(room);
        reservation.setStatus("BOOKED");
        reservationMapper.insert(reservation);
        log.info("[检查预约] 预约成功: reservationId={}, applicationId={}, date={}, slot={}, room={}, operatorId={}",
                reservation.getId(), applicationId, reserveDate, timeSlot, room, operatorId);
        return reservationMapper.selectById(reservation.getId());
    }

    /**
     * 到院报到：BOOKED → CHECKED_IN，写报到时间
     * <p>
     * 毕设简化：不联动 exam_application 状态，检查执行登记仍由技师在既有
     * ExamService.executeExam 流程中完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReservation checkin(Long reservationId) {
        ExamReservation reservation = requireReservation(reservationId);
        if (!"BOOKED".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "仅 BOOKED 状态的预约可报到，当前状态：" + reservation.getStatus());
        }
        reservationMapper.updateStatus(reservationId, "CHECKED_IN", LocalDateTime.now());
        log.info("[检查预约] 到院报到: reservationId={}, applicationId={}",
                reservationId, reservation.getApplicationId());
        return reservationMapper.selectById(reservationId);
    }

    /**
     * 更新预约状态（DONE/CANCELLED）
     * <p>
     * DONE：检查完成（CHECKED_IN → DONE）；CANCELLED：取消预约（仅 BOOKED 可取消）。
     *
     * @param reservationId 预约 ID
     * @param action        目标动作：DONE / CANCELLED
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReservation updateStatus(Long reservationId, String action) {
        ExamReservation reservation = requireReservation(reservationId);
        if ("DONE".equals(action)) {
            if (!"CHECKED_IN".equals(reservation.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "仅已报到（CHECKED_IN）的预约可标记完成，当前状态：" + reservation.getStatus());
            }
            reservationMapper.updateStatus(reservationId, "DONE", null);
        } else if ("CANCELLED".equals(action)) {
            if (!"BOOKED".equals(reservation.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "仅未报到（BOOKED）的预约可取消，当前状态：" + reservation.getStatus());
            }
            reservationMapper.updateStatus(reservationId, "CANCELLED", null);
        } else {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "action 仅支持 DONE / CANCELLED");
        }
        log.info("[检查预约] 状态变更: reservationId={}, {} → {}", reservationId, reservation.getStatus(), action);
        return reservationMapper.selectById(reservationId);
    }

    /** 预约详情 */
    public ExamReservation getById(Long reservationId) {
        return requireReservation(reservationId);
    }

    /** 按检查申请查询预约记录 */
    public List<ExamReservation> listByApplication(Long applicationId) {
        return reservationMapper.selectByApplication(applicationId);
    }

    /** 预约分页（日期/状态可选） */
    public Map<String, Object> listByPage(LocalDate reserveDate, String status, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<ExamReservation> records = reservationMapper.selectByPage(reserveDate, status, offset, pageSize);
        long total = reservationMapper.countPage(reserveDate, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 患者自助改约（迭代13 K4）：仅 BOOKED 状态可改约，重写预约日期与时段。
     *
     * @param reservationId 预约 ID
     * @param reserveDate   新预约日期
     * @param timeSlot      新预约时段
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReservation reschedule(Long reservationId, LocalDate reserveDate, String timeSlot) {
        if (reserveDate == null || timeSlot == null || timeSlot.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "新的预约日期与时段不能为空");
        }
        ExamReservation reservation = requireReservation(reservationId);
        if (!"BOOKED".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "仅未报到（BOOKED）的预约可改约，当前状态：" + reservation.getStatus());
        }
        reservationMapper.reschedule(reservationId, reserveDate, timeSlot);
        log.info("[检查预约] 患者自助改约: reservationId={}, {} {} → {} {}",
                reservationId, reservation.getReserveDate(), reservation.getTimeSlot(), reserveDate, timeSlot);
        return reservationMapper.selectById(reservationId);
    }

    private ExamReservation requireReservation(Long reservationId) {
        if (reservationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "reservationId 不能为空");
        }
        ExamReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "预约记录不存在");
        }
        return reservation;
    }
}
