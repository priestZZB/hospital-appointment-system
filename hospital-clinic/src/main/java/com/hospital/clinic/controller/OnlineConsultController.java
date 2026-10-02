package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.OnlineConsultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线上图文复诊接口（迭代13 K1，base=/api/clinic/online-consults）。
 * 患者发起 → 医生接诊 → 图文交流 → 续方（生成处方）→ 关闭。
 * 续方病历兜底：复诊单关联病历 → 患者最近病历 → 报错。
 */
@RestController
@RequestMapping("/api/clinic/online-consults")
@RequiredArgsConstructor
public class OnlineConsultController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OnlineConsultMapper consultMapper;

    /** 患者发起图文复诊 */
    @PostMapping
    @AuditLog(value = "发起图文复诊", operationType = "INSERT")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long doctorId = toLong(body.get("doctorId"));
        String complaint = str(body.get("chiefComplaint"));
        if (doctorId == null || complaint == null || complaint.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "接诊医生与主诉不能为空");
        }
        String consultNo = "OC" + LocalDateTime.now().format(NO_FMT) + String.format("%04d", RANDOM.nextInt(10000));
        consultMapper.insert(consultNo, UserContext.getUserId(), str(body.get("patientName")),
                doctorId, toLong(body.get("medicalRecordId")), complaint);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", consultMapper.selectIdByConsultNo(consultNo));
        data.put("consultNo", consultNo);
        return Result.ok(data);
    }

    /** 复诊分页：患者看自己的（patientId），医生/管理看全部（status 筛选） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "patientId", required = false) Long patientId,
                                            @RequestParam(value = "doctorId", required = false) Long doctorId,
                                            @RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "mine", required = false) Boolean mine,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Long pid = Boolean.TRUE.equals(mine) ? UserContext.getUserId() : patientId;
        List<Map<String, Object>> records = consultMapper.selectPage(pid, doctorId, status,
                (pageNo - 1) * pageSize, pageSize);
        long total = consultMapper.countPage(pid, doctorId, status);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", records);
        page.put("total", total);
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 复诊详情（含消息记录 + 患者最近病历号，供续方兜底） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        Map<String, Object> consult = consultMapper.selectById(id);
        if (consult == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "复诊单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>(consult);
        data.put("messages", consultMapper.selectMessages(id));
        Object patientId = consult.get("patientId");
        if (patientId != null) {
            data.put("latestRecordId", consultMapper.selectLatestRecord(toLong(patientId)));
        }
        return Result.ok(data);
    }

    /** 患者发送消息 */
    @PostMapping("/{id}/messages")
    public Result<String> sendMessage(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String content = str(body.get("content"));
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "消息内容不能为空");
        }
        String senderType = str(body.get("senderType"));
        if (!"PATIENT".equals(senderType) && !"DOCTOR".equals(senderType)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "senderType 需为 PATIENT 或 DOCTOR");
        }
        consultMapper.insertMessage(id, senderType, UserContext.getUserId(), str(body.get("senderName")), content);
        return Result.ok("消息已发送");
    }

    /** 医生接诊 */
    @PostMapping("/{id}/accept")
    @RequiresPermission(PermissionConstant.CONSULT_HANDLE)
    public Result<String> accept(@PathVariable("id") Long id) {
        if (consultMapper.accept(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅待接诊的复诊单可接诊");
        }
        return Result.ok("接诊成功");
    }

    /** 医生续方：生成处方（含明细）并回写复诊单 */
    @PostMapping("/{id}/prescribe")
    @Transactional
    @RequiresPermission(PermissionConstant.CONSULT_HANDLE)
    public Result<Map<String, Object>> prescribe(@PathVariable("id") Long id,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> consult = consultMapper.selectById(id);
        if (consult == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "复诊单不存在");
        }
        if (!"IN_PROGRESS".equals(str(consult.get("status")))) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅问诊中的复诊单可续方");
        }
        Long patientId = toLong(consult.get("patientId"));
        Long doctorId = toLong(consult.get("doctorId"));
        Long recordId = toLong(consult.get("medicalRecordId"));
        if (recordId == null) {
            Map<String, Object> latest = consultMapper.selectLatestRecord(patientId);
            recordId = latest == null ? null : toLong(latest.get("id"));
        }
        if (recordId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "患者无可用病历，无法续方");
        }
        String prescriptionNo = "RC" + LocalDateTime.now().format(NO_FMT) + String.format("%04d", RANDOM.nextInt(10000));
        consultMapper.insertPrescription(prescriptionNo, recordId, patientId, doctorId);
        Long prescriptionId = consultMapper.selectPrescriptionIdByNo(prescriptionNo);
        Object itemsObj = body.get("items");
        if (itemsObj instanceof List<?> items) {
            for (Object o : items) {
                if (o instanceof Map item) {
                    consultMapper.insertPrescriptionItem(prescriptionId, toLong(item.get("drugId")),
                            str(item.get("drugName")), str(item.get("specification")), str(item.get("dosage")),
                            str(item.get("usageMethod")), str(item.get("frequency")),
                            item.get("days") == null ? 1 : toInt(item.get("days")),
                            item.get("quantity") == null ? 1 : toInt(item.get("quantity")),
                            str(item.get("unit")), str(item.get("remark")));
                }
            }
        }
        consultMapper.fillPrescription(id, prescriptionId, prescriptionNo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prescriptionId", prescriptionId);
        data.put("prescriptionNo", prescriptionNo);
        return Result.ok(data);
    }

    /** 关闭复诊 */
    @PostMapping("/{id}/close")
    public Result<String> close(@PathVariable("id") Long id) {
        if (consultMapper.close(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅未结束的复诊单可关闭");
        }
        return Result.ok("复诊已结束");
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private Integer toInt(Object v) {
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
