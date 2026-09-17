package com.hospital.clinic.controller;

import com.hospital.clinic.entity.FollowUpPlan;
import com.hospital.clinic.service.FollowUpService;
import com.hospital.clinic.vo.FollowUpPlanVO;
import com.hospital.common.feign.dto.CreateFollowUpDTO;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 随访内部接口（Feign 免鉴权，走 /api/clinic/internal/** 白名单）：
 * 住院出院小结办理后自动衔接随访计划（迭代6 E4）。
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/internal")
@RequiredArgsConstructor
public class FollowUpInternalController {

    private final FollowUpService followUpService;

    @PostMapping("/follow-up")
    public Result<Map<String, Object>> createFollowUp(@RequestBody CreateFollowUpDTO dto) {
        FollowUpPlan plan = new FollowUpPlan();
        plan.setPatientId(dto.getPatientId());
        plan.setFollowDate(dto.getFollowDate());
        plan.setFollowMethod(dto.getFollowMethod() == null ? "PHONE" : dto.getFollowMethod());
        plan.setTemplate(dto.getTemplate());
        FollowUpPlanVO vo = followUpService.createPlan(dto.getDoctorUserId(), plan);
        Map<String, Object> data = new HashMap<>();
        data.put("planId", vo.getId());
        data.put("status", vo.getStatus());
        log.info("[随访内部] 住院出院衔接随访: planId={}, patientId={}", vo.getId(), dto.getPatientId());
        return Result.ok(data);
    }
}
