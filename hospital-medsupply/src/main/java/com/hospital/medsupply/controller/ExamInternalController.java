package com.hospital.medsupply.controller;

import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        ExamApplication application = new ExamApplication();
        application.setApplicationNo("EXA" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 20).toUpperCase());
        application.setMedicalRecordId(toLong(body.get("medicalRecordId")));
        application.setPatientId(toLong(body.get("patientId")));
        application.setDoctorId(toLong(body.get("doctorId")));
        application.setExamItemId(toLong(body.get("examItemId")));
        application.setExamItemName((String) body.get("examItemName"));
        application.setItemType((String) body.get("itemType"));
        application.setApplyRemark((String) body.get("applyRemark"));
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
