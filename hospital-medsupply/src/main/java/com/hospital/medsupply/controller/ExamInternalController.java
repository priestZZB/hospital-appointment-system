package com.hospital.medsupply.controller;

import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ExamItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 检查申请内部接口（供 clinic-service 通过服务间调用创建检查申请）
 * <p>
 * 对应 ConsultationService 的 POST http://medsupply-service/api/medsupply/internal/exam/apply。
 */
@Slf4j
@RestController
@RequestMapping("/api/medsupply/internal/exam")
@RequiredArgsConstructor
public class ExamInternalController {

    private final ExamApplicationMapper examApplicationMapper;
    private final ExamItemMapper examItemMapper;

    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        ExamApplication application = new ExamApplication();
        application.setApplicationNo("EXA" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 20).toUpperCase());
        application.setMedicalRecordId(toLong(body.get("medicalRecordId")));
        application.setPatientId(toLong(body.get("patientId")));
        application.setDoctorId(toLong(body.get("doctorId")));
        Long examItemId = toLong(body.get("examItemId"));
        application.setExamItemId(examItemId);
        application.setExamItemName((String) body.get("examItemName"));
        application.setItemType((String) body.get("itemType"));
        application.setApplyRemark((String) body.get("applyRemark"));
        // 划价：取检查项目参考价作为应缴金额
        BigDecimal amount = BigDecimal.ZERO;
        if (examItemId != null) {
            ExamItem item = examItemMapper.selectById(examItemId);
            if (item != null && item.getReferencePrice() != null) {
                amount = item.getReferencePrice();
            }
        }
        application.setTotalAmount(amount);
        application.setPayStatus("UNPAID");
        application.setStatus("PENDING");
        examApplicationMapper.insert(application);
        log.info("[检查申请] 已接收: applicationNo={}, examItemId={}, medicalRecordId={}",
                application.getApplicationNo(), application.getExamItemId(), application.getMedicalRecordId());
        return Map.of(
                "id", application.getId(),
                "applicationNo", application.getApplicationNo(),
                "status", application.getStatus()
        );
    }

    /** 检查缴费成功回写 pay_status=PAID 并记录实收金额 */
    @PutMapping("/{id}/paid")
    public Map<String, Object> markPaid(@PathVariable("id") Long id,
                                        @RequestParam("amount") BigDecimal amount) {
        int rows = examApplicationMapper.markPaid(id, amount, "PAID");
        log.info("[检查申请] 缴费回写: examApplicationId={}, amount={}, rows={}", id, amount, rows);
        return Map.of("success", rows > 0);
    }

    /** 检查退费回写 pay_status=REFUNDED */
    @PutMapping("/{id}/refunded")
    public Map<String, Object> markRefunded(@PathVariable("id") Long id) {
        int rows = examApplicationMapper.markRefunded(id);
        log.info("[检查申请] 退费回写: examApplicationId={}, rows={}", id, rows);
        return Map.of("success", rows > 0);
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
