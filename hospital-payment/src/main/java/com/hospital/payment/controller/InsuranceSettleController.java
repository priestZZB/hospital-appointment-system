package com.hospital.payment.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.payment.dto.InsuranceSettleDTO;
import com.hospital.payment.service.InsuranceSettleService;
import com.hospital.payment.service.InsuranceVoucherPdfService;
import com.hospital.payment.vo.InsuranceSettleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 医保结算接口（迭代11 H1~H3，/api/payment/insurance/**）。
 * <p>
 * 结算算法（常量写死）：甲类统筹 80%、乙类先行自付 15% 后剩余统筹 80%、自费 0；
 * 个人部分全额按个账支付（模拟个账余额充足，上限 99999），超出部分走现金。
 */
@RestController
@RequestMapping("/api/payment/insurance")
@RequiredArgsConstructor
public class InsuranceSettleController {

    private final InsuranceSettleService insuranceSettleService;
    private final InsuranceVoucherPdfService voucherPdfService;

    /** 医保结算：逐项解析目录 → 三色拆分 → 统筹/个账/现金 → 写结算单（票据号=结算号） */
    @AuditLog(value = "医保结算", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_SETTLE)
    @PostMapping("/settle")
    public Result<InsuranceSettleVO> settle(@RequestBody InsuranceSettleDTO dto) {
        if (dto.getOperatorId() == null) {
            dto.setOperatorId(UserContext.getUserId());
        }
        return Result.ok(insuranceSettleService.settle(dto));
    }

    /** 结算单详情（含三色拆分与逐项明细数组） */
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_SETTLE_QUERY)
    @GetMapping("/settle/{id}")
    public Result<InsuranceSettleVO> detail(@PathVariable("id") Long id) {
        return Result.ok(insuranceSettleService.getDetail(id));
    }

    /** 结算单分页（patientId/bizType 可选过滤） */
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_SETTLE_QUERY)
    @GetMapping("/settle/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(insuranceSettleService.page(patientId, bizType, pageNo, pageSize));
    }

    /** 冲正：SETTLED → REVERSED（轻量模拟，不回滚支付单） */
    @AuditLog(value = "医保结算冲正", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_REVERSE)
    @PostMapping("/settle/{id}/reverse")
    public Result<InsuranceSettleVO> reverse(@PathVariable("id") Long id) {
        return Result.ok(insuranceSettleService.reverse(id));
    }

    /** 医疗收费票据（电子）PDF（票据号=结算号） */
    @RequiresPermission(PermissionConstant.PAYMENT_INSURANCE_VOUCHER)
    @GetMapping("/{id}/voucher-pdf")
    public ResponseEntity<byte[]> voucherPdf(@PathVariable("id") Long id) {
        byte[] pdf = voucherPdfService.generateVoucher(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=insurance-voucher-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
