package com.hospital.auth.controller;

import com.hospital.auth.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据范围内部校验接口（供各微服务 Feign/HTTP 直连调用，不经过网关）
 * <p>
 * 业务服务在查询跨科室数据前，调用本接口确认用户是否已获得目标科室授权。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
public class DataScopeInternalController {

    private final DataScopeService dataScopeService;

    /**
     * 校验用户对目标科室是否有有效授权
     *
     * @param userId       用户 ID
     * @param departmentId 目标科室 ID
     * @return {authorized: true/false}
     */
    @GetMapping("/data-scope/check")
    public Map<String, Object> check(@RequestParam("userId") Long userId,
                                     @RequestParam("departmentId") Long departmentId) {
        boolean authorized = dataScopeService.hasActiveApply(userId, departmentId);
        return Map.of("authorized", authorized);
    }
}