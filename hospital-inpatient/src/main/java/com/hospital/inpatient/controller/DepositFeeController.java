package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.DepositPayDTO;
import com.hospital.inpatient.dto.FeeCreateDTO;
import com.hospital.inpatient.entity.InpatientFee;
import com.hospital.inpatient.service.DepositFeeService;
import com.hospital.inpatient.vo.DailyBillVO;
import com.hospital.inpatient.vo.DepositVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 预交金与住院费用接口：缴纳/登记/流水/每日清单/床位费日结
 */
@RestController
@RequestMapping("/api/inpatient")
@RequiredArgsConstructor
public class DepositFeeController {

    private final DepositFeeService depositFeeService;

    @AuditLog(value = "预交金缴纳", operationType = "INPATIENT_DEPOSIT_PAY")
    @RequiresPermission(PermissionConstant.INPATIENT_DEPOSIT_PAY)
    @PostMapping("/deposit/pay")
    public Result<DepositVO> payDeposit(@Valid @RequestBody DepositPayDTO dto) {
        return Result.ok(depositFeeService.payDeposit(dto, UserContext.getUserId()));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_FEE_QUERY)
    @GetMapping("/deposit/list")
    public Result<List<DepositVO>> deposits(@RequestParam("admissionId") Long admissionId) {
        return Result.ok(depositFeeService.deposits(admissionId));
    }

    @AuditLog(value = "住院费用登记", operationType = "INPATIENT_FEE_POST")
    @RequiresPermission(PermissionConstant.INPATIENT_FEE_POST)
    @PostMapping("/fee/post")
    public Result<InpatientFee> postFee(@Valid @RequestBody FeeCreateDTO dto) {
        return Result.ok(depositFeeService.postFee(dto));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_FEE_QUERY)
    @GetMapping("/fee/list")
    public Result<List<InpatientFee>> fees(@RequestParam("admissionId") Long admissionId) {
        return Result.ok(depositFeeService.fees(admissionId));
    }

    @RequiresPermission(PermissionConstant.INPATIENT_FEE_QUERY)
    @GetMapping("/fee/daily-bill")
    public Result<List<DailyBillVO>> dailyBill(@RequestParam("admissionId") Long admissionId) {
        return Result.ok(depositFeeService.dailyBill(admissionId));
    }

    @AuditLog(value = "床位费日结", operationType = "INPATIENT_FEE_DAILY")
    @RequiresPermission(PermissionConstant.INPATIENT_FEE_DAILY)
    @PostMapping("/fee/generate-bed-fees")
    public Result<Integer> generateBedFees(@RequestParam(value = "date", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(depositFeeService.generateDailyBedFees(date));
    }
}
