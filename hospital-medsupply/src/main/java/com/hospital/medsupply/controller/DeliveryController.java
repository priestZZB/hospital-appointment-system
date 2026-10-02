package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.mapper.DeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 线上购药配送接口（迭代13 K2，base=/api/medsupply/deliveries）。
 * 处方流转院内药房：创建配送单（CREATED）→ 发货（DISPATCHED）→ 送达（DELIVERED）。
 * 处方信息由前端冗余传入（跨库不校验）。
 */
@RestController
@RequestMapping("/api/medsupply/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DeliveryMapper deliveryMapper;

    /** 创建配送单 */
    @PostMapping
    @AuditLog(value = "创建购药配送单", operationType = "INSERT")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long prescriptionId = toLong(body.get("prescriptionId"));
        Long patientId = toLong(body.get("patientId"));
        String receiverName = str(body.get("receiverName"));
        String receiverPhone = str(body.get("receiverPhone"));
        String address = str(body.get("address"));
        if (prescriptionId == null || patientId == null || receiverName == null || receiverName.isBlank()
                || receiverPhone == null || receiverPhone.isBlank() || address == null || address.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "处方ID/患者/收货人与电话地址不能为空");
        }
        String deliveryNo = "DL" + LocalDateTime.now().format(NO_FMT) + String.format("%04d", RANDOM.nextInt(10000));
        deliveryMapper.insert(deliveryNo, prescriptionId, patientId, str(body.get("patientName")),
                str(body.get("drugSummary")), receiverName, receiverPhone, address, UserContext.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", deliveryMapper.selectIdByNo(deliveryNo));
        data.put("deliveryNo", deliveryNo);
        return Result.ok(data);
    }

    /** 配送单详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        Map<String, Object> delivery = deliveryMapper.selectById(id);
        if (delivery == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "配送单不存在");
        }
        return Result.ok(delivery);
    }

    /** 配送单分页（status/patientId 筛选） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "patientId", required = false) Long patientId,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", deliveryMapper.selectPage(status, patientId, (pageNo - 1) * pageSize, pageSize));
        page.put("total", deliveryMapper.countPage(status, patientId));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 发货 */
    @PutMapping("/{id}/dispatch")
    @AuditLog(value = "配送单发货", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.DELIVERY_MANAGE)
    public Result<String> dispatch(@PathVariable("id") Long id) {
        if (deliveryMapper.dispatch(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已创建的配送单可发货");
        }
        return Result.ok("已发货");
    }

    /** 送达确认 */
    @PutMapping("/{id}/deliver")
    @RequiresPermission(PermissionConstant.DELIVERY_MANAGE)
    public Result<String> deliver(@PathVariable("id") Long id) {
        if (deliveryMapper.deliver(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已发货的配送单可确认送达");
        }
        return Result.ok("已送达");
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
