package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.PrescriptionReview;
import com.hospital.medsupply.service.PrescriptionReviewService;
import com.hospital.medsupply.vo.PrescriptionReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 处方点评接口（迭代6 功能补全）
 */
@RestController
@RequestMapping("/api/medsupply/prescription-review")
@RequiredArgsConstructor
public class PrescriptionReviewController {

    private final PrescriptionReviewService reviewService;

    @AuditLog(value = "处方点评", operationType = "CREATE_PRESCRIPTION_REVIEW")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_PRESCRIPTION_REVIEW_CREATE)
    @PostMapping
    public Result<PrescriptionReviewVO> create(@RequestBody PrescriptionReview review) {
        requirePharmacistOrAdmin();
        return Result.ok(reviewService.create(UserContext.getUserId(), review));
    }

    @RequiresPermission(PermissionConstant.MEDSUPPLY_PRESCRIPTION_REVIEW_QUERY)
    @GetMapping
    public Result<List<PrescriptionReviewVO>> list(@RequestParam(value = "prescriptionId", required = false) Long prescriptionId,
                                                   @RequestParam(value = "pharmacistId", required = false) Long pharmacistId) {
        return Result.ok(reviewService.list(prescriptionId, pharmacistId));
    }

    private void requirePharmacistOrAdmin() {
        if (!UserContext.isPharmacistOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅药师或管理员可进行处方点评");
        }
    }
}
