package com.hospital.clinic.controller;

import com.hospital.clinic.service.BiService;
import com.hospital.clinic.vo.BiOverviewVO;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BI 统计接口
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class BiController {

    private final BiService biService;

    /**
     * BI 统计概览（当日概览/近7日趋势/科室占比）
     */
    @GetMapping("/bi/overview")
    public Result<BiOverviewVO> getOverview() {
        return Result.ok(biService.getOverview());
    }
}
