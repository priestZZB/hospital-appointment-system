package com.hospital.clinic.controller;

import com.hospital.clinic.entity.PrescriptionItem;
import com.hospital.clinic.entity.Prescription;
import com.hospital.clinic.mapper.PrescriptionItemMapper;
import com.hospital.clinic.mapper.PrescriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处方内部接口（供 medsupply-service 处方审核/发药确认时 Feign 调用）
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/internal/prescription")
@RequiredArgsConstructor
public class PrescriptionInternalController {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;

    /** 查询处方明细（drugId/drugName/规格/数量/单位） */
    @GetMapping("/{id}/items")
    public List<Map<String, Object>> items(@PathVariable("id") Long prescriptionId) {
        Prescription prescription = prescriptionMapper.selectById(prescriptionId);
        Long patientId = prescription != null ? prescription.getPatientId() : null;
        return prescriptionItemMapper.selectByPrescriptionId(prescriptionId).stream()
                .map(this::toMap)
                .peek(m -> m.put("patientId", patientId))
                .collect(Collectors.toList());
    }

    /** 更新处方状态（状态机：PENDING_REVIEW → REVIEW_PASSED/REVIEW_REJECTED → DISPENSED） */
    @PostMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable("id") Long prescriptionId,
                                            @RequestParam("status") String status,
                                            @RequestParam(value = "expectedStatus", required = false) String expectedStatus,
                                            @RequestParam(value = "reviewComment", required = false) String reviewComment) {
        int rows = prescriptionMapper.updateStatus(prescriptionId, status, reviewComment, expectedStatus);
        log.info("[处方内部] 状态更新: prescriptionId={}, status={}, rows={}", prescriptionId, status, rows);
        return Map.of("success", rows > 0);
    }

    private Map<String, Object> toMap(PrescriptionItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("drugId", item.getDrugId());
        map.put("drugName", item.getDrugName());
        map.put("specification", item.getSpecification());
        map.put("quantity", item.getQuantity());
        map.put("unit", item.getUnit());
        map.put("dosage", item.getDosage());
        map.put("usageMethod", item.getUsageMethod());
        map.put("frequency", item.getFrequency());
        map.put("days", item.getDays());
        map.put("remark", item.getRemark());
        return map;
    }
}
