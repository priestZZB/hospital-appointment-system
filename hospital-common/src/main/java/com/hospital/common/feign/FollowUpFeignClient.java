package com.hospital.common.feign;

import com.hospital.common.feign.dto.CreateFollowUpDTO;
import com.hospital.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 随访计划内部 Feign（住院出院自动衔接随访，E4） */
@FeignClient(name = "clinic-service", path = "/api/clinic/internal", contextId = "followUpInternalClient")
public interface FollowUpFeignClient {

    @PostMapping("/follow-up")
    Result<Map<String, Object>> createFollowUp(@RequestBody CreateFollowUpDTO dto);
}
