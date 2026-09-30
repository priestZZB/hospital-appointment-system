package com.hospital.medsupply.controller;

import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.NarcoticRegister;
import com.hospital.medsupply.service.NarcoticService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 麻精药品五专登记查询接口（仅药师/管理员）
 * <p>
 * 登记流水由采购入库/发药/退药/报损业务自动写入（内部调用），
 * 本接口仅提供五专登记册查询。
 */
@RestController
@RequestMapping("/api/admin/drug/narcotic")
@RequiredArgsConstructor
public class NarcoticRegisterController {

    private final NarcoticService narcoticService;

    /** 五专登记册查询（按药品，最新流水在前） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_NARCOTIC_QUERY)
    @GetMapping("/list")
    public Result<List<NarcoticRegister>> list(@RequestParam("drugId") Long drugId) {
        requirePharmacist();
        return Result.ok(narcoticService.listByDrugId(drugId));
    }

    private void requirePharmacist() {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可执行此操作");
        }
    }
}
