package com.hospital.medsupply.controller;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 医生开处方用药品查询接口（医生/管理员可调用）
 */
@RestController
@RequestMapping("/api/medsupply")
@RequiredArgsConstructor
public class DrugQueryController {

    private final DrugService drugService;

    /** 药品目录分页查询（keyword 模糊搜索：名称/编码/通用名） */
    @GetMapping("/drugs")
    public Result<Map<String, Object>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        checkDoctorOrAdmin();
        return Result.ok(drugService.page(keyword, pageNo, pageSize));
    }

    private void checkDoctorOrAdmin() {
        if (!UserContext.hasRole("ROLE_DOCTOR") && !UserContext.hasRole("ROLE_ADMIN")) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
        }
    }
}
