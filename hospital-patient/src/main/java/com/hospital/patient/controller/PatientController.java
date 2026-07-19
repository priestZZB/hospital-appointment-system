package com.hospital.patient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.patient.dto.UpdatePatientDTO;
import com.hospital.patient.service.PatientService;
import com.hospital.patient.vo.PatientVO;
import com.hospital.patient.vo.VisitCardVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者档案接口
 */
@Slf4j
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    /** 查询本人档案 */
    @GetMapping("/profile")
    public Result<PatientVO> getProfile() {
        Long userId = UserContext.getUserId();
        return Result.ok(patientService.getProfile(userId));
    }

    /** 编辑本人档案 */
    @AuditLog(value = "编辑患者档案", operationType = "UPDATE")
    @PutMapping("/profile")
    public Result<PatientVO> updateProfile(@Valid @RequestBody UpdatePatientDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(patientService.updateProfile(userId, dto));
    }

    /** 查询本人就诊卡列表 */
    @GetMapping("/visit-cards")
    public Result<List<VisitCardVO>> getVisitCards() {
        Long userId = UserContext.getUserId();
        return Result.ok(patientService.getVisitCards(userId));
    }
}
