package com.hospital.patient.controller;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.dto.CreatePatientDTO;
import com.hospital.patient.entity.Patient;
import com.hospital.patient.mapper.PatientMapper;
import com.hospital.patient.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 患者档案内部接口（供 Feign 调用）
 * <p>
 * WARNING: 这些内部端点依赖网络层隔离（仅限内网访问），
 * 建议在网关或 Kubernetes 网络策略层面限制外部流量。
 * 生产环境应配置 internal.api.token 头部校验或 mTLS。
 */
@Slf4j
@RestController
@RequestMapping("/api/patient/internal")
@RequiredArgsConstructor
public class PatientInternalController {

    private final PatientService patientService;
    private final PatientMapper patientMapper;

    /** Feign: auth-service 注册时创建患者档案 */
    @PostMapping("/create")
    public Long createPatient(@Valid @RequestBody CreatePatientDTO dto) {
        if (dto.getUserId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "userId 不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "name 不能为空");
        }
        if (dto.getPhone() == null || dto.getPhone().isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "phone 不能为空");
        }
        Patient patient = new Patient();
        patient.setUserId(dto.getUserId());
        patient.setName(dto.getName());
        patient.setPhone(dto.getPhone());
        patient.setGender(0);
        patient.setVerifyStatus(0);
        patientMapper.insert(patient);
        log.info("[内部] 患者档案创建: patientId={}, userId={}", patient.getId(), dto.getUserId());
        return patient.getId();
    }

    /** Feign: 根据 userId 查询 */
    @GetMapping("/byUserId")
    public Map<String, Object> getByUserId(@RequestParam("userId") Long userId) {
        Patient p = patientService.getByUserId(userId);
        return toMap(p);
    }

    /** Feign: 根据 patientId 查询 */
    @GetMapping("/byId")
    public Map<String, Object> getById(@RequestParam("patientId") Long patientId) {
        Patient p = patientService.getById(patientId);
        return toMap(p);
    }

    private Map<String, Object> toMap(Patient p) {
        Map<String, Object> map = new HashMap<>();
        if (p != null) {
            map.put("id", p.getId());
            map.put("userId", p.getUserId());
            map.put("name", p.getName());
            map.put("phone", p.getPhone());
            map.put("verifyStatus", p.getVerifyStatus());
        }
        return map;
    }
}
