package com.hospital.clinic.controller;

import com.hospital.clinic.entity.Prescription;
import com.hospital.clinic.entity.PrescriptionItem;
import com.hospital.clinic.mapper.PrescriptionItemMapper;
import com.hospital.clinic.mapper.PrescriptionMapper;
import com.hospital.clinic.vo.PrescriptionItemVO;
import com.hospital.clinic.vo.PrescriptionVO;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处方查询接口（患者端 / 收费台）
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final PatientFeignClient patientFeignClient;

    /**
     * 查询患者未缴费处方列表（患者本人 / 收费员 / 管理员）
     */
    @RequiresPermission(PermissionConstant.CLINIC_PRESCRIPTION_QUERY)
    @GetMapping("/prescription/unpaid")
    public Result<List<PrescriptionVO>> unpaid(@RequestParam("patientId") Long patientId) {
        Long userId = UserContext.getUserId();
        if (!UserContext.isCashierOrAdmin()) {
            Long myPatientId = resolvePatientId(userId);
            if (myPatientId == null || !myPatientId.equals(patientId)) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权查看他人处方");
            }
        }
        List<Prescription> prescriptions = prescriptionMapper.selectUnpaidByPatient(patientId);
        List<PrescriptionVO> vos = prescriptions.stream()
                .map(p -> toPrescriptionVO(p, prescriptionItemMapper.selectByPrescriptionId(p.getId())))
                .collect(Collectors.toList());
        return Result.ok(vos);
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
            Object pidObj = patientInfo.get("id");
            if (pidObj == null) {
                return null;
            }
            return Long.valueOf(pidObj.toString());
        } catch (Exception e) {
            log.warn("[处方] 查询患者信息失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 处方实体 → VO（含明细）
     */
    private PrescriptionVO toPrescriptionVO(Prescription p, List<PrescriptionItem> items) {
        List<PrescriptionItemVO> itemVOs = items.stream()
                .map(i -> PrescriptionItemVO.builder()
                        .id(i.getId()).prescriptionId(i.getPrescriptionId())
                        .drugId(i.getDrugId()).drugName(i.getDrugName())
                        .specification(i.getSpecification()).dosage(i.getDosage())
                        .usageMethod(i.getUsageMethod()).frequency(i.getFrequency())
                        .days(i.getDays()).quantity(i.getQuantity())
                        .unit(i.getUnit()).remark(i.getRemark())
                        .decoctionMethod(i.getDecoctionMethod()).footnote(i.getFootnote())
                        .build())
                .collect(Collectors.toList());

        return PrescriptionVO.builder()
                .id(p.getId()).prescriptionNo(p.getPrescriptionNo())
                .medicalRecordId(p.getMedicalRecordId()).patientId(p.getPatientId())
                .doctorId(p.getDoctorId()).status(p.getStatus())
                .reviewComment(p.getReviewComment())
                .totalAmount(p.getTotalAmount()).payStatus(p.getPayStatus())
                .prescriptionType(p.getPrescriptionType())
                .herbalDoses(p.getHerbalDoses()).herbalUsage(p.getHerbalUsage())
                .items(itemVOs)
                .createTime(p.getCreateTime()).build();
    }
}
