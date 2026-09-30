package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.inpatient.dto.AnesthesiaRecordDTO;
import com.hospital.inpatient.dto.InformedConsentDTO;
import com.hospital.inpatient.dto.OutpatientSurgeryDTO;
import com.hospital.inpatient.dto.PreopAssessmentDTO;
import com.hospital.inpatient.dto.SurgeryRecordDTO;
import com.hospital.inpatient.dto.SurgeryScheduleDTO;
import com.hospital.inpatient.service.SurgeryService;
import com.hospital.inpatient.vo.AnesthesiaRecordVO;
import com.hospital.inpatient.vo.InformedConsentVO;
import com.hospital.inpatient.vo.PreopAssessmentVO;
import com.hospital.inpatient.vo.SurgeryBoardVO;
import com.hospital.inpatient.vo.SurgeryDetailVO;
import com.hospital.inpatient.vo.SurgeryRecordVO;
import com.hospital.inpatient.vo.SurgeryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 手术/麻醉中心接口（迭代10 F1~F5，统一手术单）。
 * <p>
 * 与既有 {@link SurgeryApplyController}（住院手术申请单，/api/inpatient/surgery 同前缀）并存：
 * 排台/撤台由原申请单 POST /{id}/schedule、POST /{id}/cancel 升级为统一手术单的
 * PUT /{id}/schedule、PUT /{id}/cancel（JSON body），二者 HTTP 方法与语义不同，互不冲突。
 * <p>
 * 状态机：APPLIED --排台--> SCHEDULED --评估PASSED--> PREOP_PASSED --开始--> IN_OPERATION
 * --手术记录--> OPERATED；APPLIED/SCHEDULED 可 CANCELLED。
 */
@RestController
@RequestMapping("/api/inpatient/surgery")
@RequiredArgsConstructor
public class SurgeryController {

    private final SurgeryService surgeryService;

    /** F1：门诊手术建单（source=OUTPATIENT，status=APPLIED，建单即生成手术单号） */
    @AuditLog(value = "门诊手术建单", operationType = "INPATIENT_SURGERY_OUTPATIENT_CREATE")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_OUTPATIENT_CREATE)
    @PostMapping("/outpatient")
    public Result<SurgeryVO> createOutpatient(@Valid @RequestBody OutpatientSurgeryDTO dto) {
        return Result.ok(surgeryService.createOutpatient(dto));
    }

    /** F2：统一手术单排台（校验 status=APPLIED → SCHEDULED） */
    @AuditLog(value = "手术排台", operationType = "INPATIENT_SURGERY_CENTER_SCHEDULE")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_CENTER_SCHEDULE)
    @PutMapping("/{id}/schedule")
    public Result<SurgeryVO> schedule(@PathVariable("id") Long id,
                                      @Valid @RequestBody SurgeryScheduleDTO dto) {
        return Result.ok(surgeryService.schedule(id, dto));
    }

    /**
     * 撤台/取消（APPLIED/SCHEDULED → CANCELLED，状态机闭环）。
     * 用 PUT 与既有申请单撤单 POST /{id}/cancel（SurgeryApplyController）区分，避免映射冲突。
     */
    @AuditLog(value = "取消手术单", operationType = "INPATIENT_SURGERY_CANCEL")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_CENTER_SCHEDULE)
    @PutMapping("/{id}/cancel")
    public Result<SurgeryVO> cancel(@PathVariable("id") Long id) {
        return Result.ok(surgeryService.cancel(id));
    }

    /** F3：提交术前评估（ASA 1~5；UNIQUE(surgery_id) 重复提交覆盖；PASSED 且已排台 → PREOP_PASSED） */
    @AuditLog(value = "术前评估提交", operationType = "INPATIENT_SURGERY_PREOP")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_PREOP_SUBMIT)
    @PostMapping("/{id}/preop")
    public Result<PreopAssessmentVO> submitPreop(@PathVariable("id") Long id,
                                                 @Valid @RequestBody PreopAssessmentDTO dto) {
        return Result.ok(surgeryService.submitPreop(id, dto, UserContext.getUserId()));
    }

    /** F3：签署知情同意书（SURGERY/ANESTHESIA 各一条，重复签署覆盖） */
    @AuditLog(value = "知情同意签署", operationType = "INPATIENT_SURGERY_CONSENT")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_CONSENT_SIGN)
    @PostMapping("/{id}/consent")
    public Result<InformedConsentVO> signConsent(@PathVariable("id") Long id,
                                                 @Valid @RequestBody InformedConsentDTO dto) {
        return Result.ok(surgeryService.signConsent(id, dto));
    }

    /** F4：手术开始（校验 SCHEDULED/PREOP_PASSED → IN_OPERATION；未评估开始记 warning） */
    @AuditLog(value = "手术开始", operationType = "INPATIENT_SURGERY_START")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_START)
    @PostMapping("/{id}/start")
    public Result<SurgeryVO> start(@PathVariable("id") Long id) {
        return Result.ok(surgeryService.start(id));
    }

    /** F4：手术记录录入（校验 IN_OPERATION；一单一条；完成 → OPERATED） */
    @AuditLog(value = "手术记录录入", operationType = "INPATIENT_SURGERY_RECORD")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_RECORD_ENTRY)
    @PostMapping("/{id}/record")
    public Result<SurgeryRecordVO> saveRecord(@PathVariable("id") Long id,
                                              @Valid @RequestBody SurgeryRecordDTO dto) {
        return Result.ok(surgeryService.saveRecord(id, dto));
    }

    /** F4：麻醉记录录入（一单一条；要求已排台未取消） */
    @AuditLog(value = "麻醉记录录入", operationType = "INPATIENT_SURGERY_ANESTHESIA")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_ANESTHESIA_ENTRY)
    @PostMapping("/{id}/anesthesia")
    public Result<AnesthesiaRecordVO> saveAnesthesia(@PathVariable("id") Long id,
                                                     @Valid @RequestBody AnesthesiaRecordDTO dto) {
        return Result.ok(surgeryService.saveAnesthesia(id, dto));
    }

    /** F5：术后随访触发（校验 OPERATED；Feign 创建「术后镇痛随访」，fail-open） */
    @AuditLog(value = "术后随访触发", operationType = "INPATIENT_SURGERY_POSTOP_FOLLOWUP")
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_POSTOP_FOLLOWUP)
    @PostMapping("/{id}/postop-followup")
    public Result<Map<String, Object>> postopFollowup(@PathVariable("id") Long id) {
        return Result.ok(surgeryService.postopFollowup(id, UserContext.getUserId()));
    }

    /** 手术排台看板（当日列表含手术/患者/主刀/麻醉方式/状态/评估结论；date 缺省今天） */
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_BOARD)
    @GetMapping("/board")
    public Result<List<SurgeryBoardVO>> board(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(surgeryService.board(date));
    }

    /** 详情聚合（手术单+术前评估+双同意书+手术记录+麻醉记录） */
    @RequiresPermission({PermissionConstant.INPATIENT_SURGERY_BOARD, PermissionConstant.INPATIENT_SURGERY_LIST})
    @GetMapping("/{id}")
    public Result<SurgeryDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(surgeryService.detail(id));
    }

    /** 分页查询（status/source 筛选） */
    @RequiresPermission(PermissionConstant.INPATIENT_SURGERY_LIST)
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(surgeryService.page(status, source, pageNo, pageSize));
    }
}
