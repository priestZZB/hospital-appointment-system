package com.hospital.inpatient.service;

import cn.hutool.core.lang.UUID;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.MedicalOrderCreateDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.InpatientMedicalOrder;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.InpatientMedicalOrderMapper;
import com.hospital.inpatient.mapper.OrderExecutionMapper;
import com.hospital.inpatient.vo.MedicalOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 住院医嘱服务：开立 -> 护士核对 -> 执行 -> 停止
 * <p>
 * 状态机：OPEN -> CONFIRMED -> EXECUTING -> COMPLETED / STOPPED
 * 全链路留时间戳，供迭代6 阶段B 算法层（执行及时率）与 AI 层使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalOrderService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final InpatientMedicalOrderMapper orderMapper;
    private final OrderExecutionMapper executionMapper;
    private final AdmissionMapper admissionMapper;

    /** 医生开立医嘱 */
    @Transactional(rollbackFor = Exception.class)
    public MedicalOrderVO create(Long doctorUserId, MedicalOrderCreateDTO dto) {
        Admission admission = requireActive(dto.getAdmissionId());
        InpatientMedicalOrder order = new InpatientMedicalOrder();
        order.setOrderNo("IZY" + LocalDateTime.now().format(NO_FMT)
                + UUID.fastUUID().toString().substring(0, 4).toUpperCase());
        order.setAdmissionId(admission.getId());
        order.setDoctorId(doctorUserId);
        order.setOrderType(dto.getOrderType());
        order.setCategory(dto.getCategory());
        order.setContent(dto.getContent());
        order.setFrequency(dto.getFrequency());
        order.setStatus("OPEN");
        order.setOpenTime(LocalDateTime.now());
        orderMapper.insert(order);
        log.info("[住院医嘱] 开立: id={}, admissionId={}, type={}, category={}",
                order.getId(), admission.getId(), dto.getOrderType(), dto.getCategory());
        return toVO(order);
    }

    /** 护士核对（OPEN -> CONFIRMED） */
    @Transactional(rollbackFor = Exception.class)
    public MedicalOrderVO confirm(Long nurseUserId, Long orderId) {
        InpatientMedicalOrder order = requireOrder(orderId);
        if (orderMapper.confirm(orderId, nurseUserId) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "医嘱已核对或状态已变更");
        }
        log.info("[住院医嘱] 核对: id={}, nurseId={}", orderId, nurseUserId);
        return toVO(orderMapper.selectById(orderId));
    }

    /**
     * 护士执行：临时医嘱执行即完成；长期医嘱首次执行转 EXECUTING，可反复执行
     */
    @Transactional(rollbackFor = Exception.class)
    public MedicalOrderVO execute(Long nurseUserId, Long orderId, String result) {
        InpatientMedicalOrder order = requireOrder(orderId);
        if ("OPEN".equals(order.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "医嘱尚未核对，不可执行");
        }
        if ("COMPLETED".equals(order.getStatus()) || "STOPPED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "医嘱已结束，不可执行");
        }
        executionMapper.insert(orderId, order.getAdmissionId(), nurseUserId, result);
        String next = "TEMPORARY".equals(order.getOrderType()) ? "COMPLETED" : "EXECUTING";
        orderMapper.updateExecStatus(orderId, next);
        log.info("[住院医嘱] 执行: id={}, nurseId={}, next={}", orderId, nurseUserId, next);
        return toVO(orderMapper.selectById(orderId));
    }

    /** 医生停止医嘱（长期医嘱结束） */
    @Transactional(rollbackFor = Exception.class)
    public void stop(Long doctorUserId, Long orderId) {
        InpatientMedicalOrder order = requireOrder(orderId);
        if (!Objects.equals(order.getDoctorId(), doctorUserId)
                && !com.hospital.common.interceptor.UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅开立医生可停止医嘱");
        }
        if (orderMapper.stop(orderId) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "医嘱已结束");
        }
        log.info("[住院医嘱] 停止: id={}, doctorId={}", orderId, doctorUserId);
    }

    /** 医嘱列表（按住院记录） */
    public List<MedicalOrderVO> listOrders(Long admissionId, String status) {
        return orderMapper.selectList(admissionId, status, null).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    /** 校验住院记录存在且在院（开立医嘱前置） */
    private Admission requireActive(Long admissionId) {
        Admission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        if (!"ADMITTED".equals(admission.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已出院，不可开立医嘱");
        }
        return admission;
    }

    private InpatientMedicalOrder requireOrder(Long orderId) {
        InpatientMedicalOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "医嘱不存在");
        }
        return order;
    }

    private MedicalOrderVO toVO(InpatientMedicalOrder o) {
        MedicalOrderVO vo = new MedicalOrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setAdmissionId(o.getAdmissionId());
        vo.setDoctorId(o.getDoctorId());
        vo.setOrderType(o.getOrderType());
        vo.setCategory(o.getCategory());
        vo.setContent(o.getContent());
        vo.setFrequency(o.getFrequency());
        vo.setStatus(o.getStatus());
        vo.setOpenTime(o.getOpenTime());
        vo.setConfirmTime(o.getConfirmTime());
        vo.setStopTime(o.getStopTime());
        vo.setCreateTime(o.getCreateTime());
        return vo;
    }
}
