package com.hospital.payment.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.payment.entity.SettleRecord;
import com.hospital.payment.service.PaymentService;
import com.hospital.payment.service.PdfService;
import com.hospital.payment.service.SettleService;
import com.hospital.payment.service.TreatmentPaymentService;
import com.hospital.payment.vo.PaymentOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 支付接口
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PdfService pdfService;
    private final TreatmentPaymentService treatmentPaymentService;
    private final SettleService settleService;

    /** 模拟支付 */
    @AuditLog(value = "模拟支付", operationType = "UPDATE")
    @PostMapping("/pay")
    public Result<PaymentOrderVO> pay(@RequestParam("orderId") Long orderId) {
        return Result.ok(paymentService.processPayment(orderId, UserContext.getUserId()));
    }

    /** 查询订单状态 */
    @GetMapping("/status/{orderId}")
    public Result<PaymentOrderVO> getStatus(@PathVariable Long orderId) {
        return Result.ok(paymentService.getStatus(orderId, UserContext.getUserId()));
    }

    /** 下载挂号凭证 PDF */
    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long orderId) {
        paymentService.checkOrderOwner(orderId, UserContext.getUserId());
        byte[] pdf = pdfService.generateReceipt(orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + orderId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** 扫表兜底（管理员手动触发） */
    @AuditLog(value = "扫表关单", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_SCAN_TIMEOUT)
    @PostMapping("/scan-timeout")
    public Result<Void> scanTimeout() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可触发扫表关单");
        }
        paymentService.scanTimeoutOrders();
        return Result.ok();
    }

    /** 创建诊疗费订单（患者） */
    @AuditLog(value = "创建诊疗费订单", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.PAYMENT_TREATMENT_CREATE)
    @PostMapping("/treatment/order")
    public Result<Map<String, Object>> createTreatmentOrder(@RequestBody Map<String, Object> body) {
        if (!UserContext.isPatient()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅患者可创建诊疗费订单");
        }
        Long patientId = treatmentPaymentService.resolvePatientId(UserContext.getUserId());
        if (patientId == null) {
            throw new BusinessException(ErrorCodeEnum.PATIENT_NOT_VERIFIED, "患者档案不存在");
        }
        String orderType = (String) body.getOrDefault("orderType", "TREATMENT");
        List<Map<String, Object>> items = castItems(body.get("items"));
        Long relatedId = toLong(body.get("relatedId"));
        return Result.ok(treatmentPaymentService.createTreatmentOrder(patientId, orderType, items, relatedId));
    }

    /** 支付诊疗费（患者） */
    @AuditLog(value = "支付诊疗费", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_TREATMENT_PAY)
    @PostMapping("/treatment/pay/{orderId}")
    public Result<PaymentOrderVO> payTreatment(@PathVariable Long orderId) {
        if (!UserContext.isPatient()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅患者可支付诊疗费");
        }
        return Result.ok(treatmentPaymentService.payTreatmentOrder(orderId, UserContext.getUserId()));
    }

    /** 收费员代患者建单（patientId 由 body 传入，非从 UserContext 解析） */
    @AuditLog(value = "收费员代缴费建单", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.PAYMENT_CASHIER_ORDER)
    @PostMapping("/cashier/order")
    public Result<Map<String, Object>> cashierCreateOrder(@RequestBody Map<String, Object> body) {
        if (!UserContext.isCashierOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅收费员或管理员可操作");
        }
        Long patientId = toLong(body.get("patientId"));
        if (patientId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "缺少患者ID");
        }
        String orderType = (String) body.getOrDefault("orderType", "TREATMENT");
        List<Map<String, Object>> items = castItems(body.get("items"));
        Long relatedId = toLong(body.get("relatedId"));
        return Result.ok(treatmentPaymentService.createTreatmentOrder(patientId, orderType, items, relatedId));
    }

    /** 收费员代患者缴费（cashierId = 当前登录用户） */
    @AuditLog(value = "收费员代缴费", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_CASHIER_PAY)
    @PostMapping("/cashier/pay/{orderId}")
    public Result<PaymentOrderVO> cashierPay(@PathVariable Long orderId) {
        if (!UserContext.isCashierOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅收费员或管理员可操作");
        }
        return Result.ok(treatmentPaymentService.payTreatmentOrder(
                orderId, UserContext.getUserId(), UserContext.getUserId()));
    }

    /** 收费员诊疗费退费 */
    @AuditLog(value = "诊疗费退费", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.PAYMENT_CASHIER_REFUND)
    @PostMapping("/cashier/refund")
    public Result<Void> cashierRefund(@RequestBody Map<String, Object> body) {
        if (!UserContext.isCashierOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅收费员或管理员可操作");
        }
        Long orderId = toLong(body.get("orderId"));
        String refundReason = (String) body.getOrDefault("refundReason", "收费员退费");
        treatmentPaymentService.refundTreatmentOrder(orderId, refundReason, UserContext.getUserId());
        return Result.ok();
    }

    /** 今日已支付订单汇总（日结对账） */
    @RequiresPermission(PermissionConstant.PAYMENT_SETTLE_QUERY)
    @GetMapping("/cashier/settle/today")
    public Result<Map<String, Object>> settleToday() {
        if (!UserContext.isCashierOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅收费员或管理员可操作");
        }
        return Result.ok(settleService.todaySummary());
    }

    /** 生成当日日结单（幂等：同一收费员 + 日期唯一） */
    @AuditLog(value = "收费员日结", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.PAYMENT_SETTLE_CREATE)
    @PostMapping("/cashier/settle")
    public Result<SettleRecord> settle() {
        if (!UserContext.isCashierOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅收费员或管理员可操作");
        }
        return Result.ok(settleService.createSettle(UserContext.getUserId()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(Object items) {
        if (items instanceof List) {
            return (List<Map<String, Object>>) items;
        }
        return null;
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
