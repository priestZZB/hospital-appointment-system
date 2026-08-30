package com.hospital.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 数据范围 Feign 客户端（迭代 5 阶段 3）
 * <p>
 * 供各业务服务（clinic 等）在查询跨科室数据前校验用户是否已获得目标科室授权
 * （跨科室授权通过 auth 服务的 data_scope_apply 申请审批产生）。
 */
@FeignClient(name = "auth-service", path = "/api/auth/internal")
public interface DataScopeFeignClient {

    /**
     * 校验用户对目标科室是否有有效授权
     *
     * @param userId       用户 ID
     * @param departmentId 目标科室 ID
     * @return {authorized: true/false}
     */
    @GetMapping("/data-scope/check")
    Map<String, Object> check(@RequestParam("userId") Long userId,
                              @RequestParam("departmentId") Long departmentId);
}