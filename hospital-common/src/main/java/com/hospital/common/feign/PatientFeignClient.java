package com.hospital.common.feign;

import com.hospital.common.feign.dto.CreatePatientDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 患者档案服务 Feign 客户端
 * <p>
 * 供其他微服务（auth-service、clinic-service 等）内部调用患者档案相关接口。
 */
@FeignClient(name = "patient-service", path = "/api/patient")
public interface PatientFeignClient {

    /**
     * 创建患者电子档案（内部调用，auth-service 注册时触发）
     */
    @PostMapping("/internal/create")
    Long createPatient(@RequestBody CreatePatientDTO dto);

    /**
     * 根据 userId 查询患者档案（内部调用，clinic-service 校验实名认证时使用）
     */
    @GetMapping("/internal/byUserId")
    Map<String, Object> getByUserId(@RequestParam("userId") Long userId);

    /**
     * 根据 patientId 查询患者档案
     */
    @GetMapping("/internal/byId")
    Map<String, Object> getById(@RequestParam("patientId") Long patientId);
}
