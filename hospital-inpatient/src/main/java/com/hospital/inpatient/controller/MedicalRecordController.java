package com.hospital.inpatient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.result.Result;
import com.hospital.inpatient.entity.MedicalRecord;
import com.hospital.inpatient.entity.RecordBorrow;
import com.hospital.inpatient.mapper.MedicalRecordMapper;
import com.hospital.inpatient.mapper.RecordBorrowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 病案借阅/归档接口（迭代12 I3）。
 * 登记（IN_WARD）→ 归档（ARCHIVED）→ 借出（BORROWED，归档后才可借、未还不重复借）→ 归还（RETURNED）。
 */
@RestController
@RequestMapping("/api/inpatient/medical-record")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordMapper medicalRecordMapper;
    private final RecordBorrowMapper recordBorrowMapper;

    @PostMapping
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    @AuditLog("病案登记")
    public Result<MedicalRecord> create(@RequestBody MedicalRecord record) {
        if (record.getPatientId() == null) {
            return Result.fail(1001, "患者不能为空");
        }
        medicalRecordMapper.insert(record);
        return Result.ok(medicalRecordMapper.selectById(record.getId()));
    }

    @GetMapping("/{id}")
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        MedicalRecord record = medicalRecordMapper.selectById(id);
        if (record == null) {
            return Result.fail(1001, "病案不存在");
        }
        return Result.ok(Map.of("record", record,
                "borrows", recordBorrowMapper.selectByRecord(id)));
    }

    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(required = false) String archiveStatus,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") Integer pageNo,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<MedicalRecord> records = medicalRecordMapper.selectPage(archiveStatus, keyword, (pageNo - 1) * size, size);
        long total = medicalRecordMapper.countPage(archiveStatus, keyword);
        return Result.ok(Map.of("records", records, "total", total, "pageNo", pageNo, "pageSize", size));
    }

    @PutMapping("/{id}/archive")
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    @AuditLog("病案归档")
    public Result<String> archive(@PathVariable Long id) {
        return medicalRecordMapper.archive(id) > 0
                ? Result.ok("归档完成")
                : Result.fail(1001, "仅未归档（IN_WARD）病案可归档");
    }

    @PostMapping("/{id}/borrow")
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    @AuditLog("病案借出")
    public Result<RecordBorrow> borrow(@PathVariable Long id, @RequestBody RecordBorrow borrow) {
        MedicalRecord record = medicalRecordMapper.selectById(id);
        if (record == null) {
            return Result.fail(1001, "病案不存在");
        }
        if (!"ARCHIVED".equals(record.getArchiveStatus())) {
            return Result.fail(1001, "病案归档后方可借阅");
        }
        if (recordBorrowMapper.countBorrowed(id) > 0) {
            return Result.fail(1001, "该病案有未归还借阅记录，不可重复借出");
        }
        if (borrow.getBorrowerName() == null || borrow.getBorrowerName().isBlank()) {
            return Result.fail(1001, "借阅人不能为空");
        }
        borrow.setRecordId(id);
        recordBorrowMapper.insert(borrow);
        return Result.ok(recordBorrowMapper.selectById(borrow.getId()));
    }

    @PostMapping("/{recordId}/return/{borrowId}")
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    @AuditLog("病案归还")
    public Result<String> returnBack(@PathVariable Long recordId, @PathVariable Long borrowId) {
        return recordBorrowMapper.returnBack(borrowId, recordId) > 0
                ? Result.ok("归还完成")
                : Result.fail(1001, "借阅记录不存在或已归还");
    }

    @GetMapping("/borrow/list")
    @RequiresPermission(PermissionConstant.MEDICAL_RECORD_MANAGE)
    public Result<Map<String, Object>> borrowList(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") Integer pageNo,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<RecordBorrow> records = recordBorrowMapper.selectPage(status, (pageNo - 1) * size, size);
        long total = recordBorrowMapper.countPage(status);
        return Result.ok(Map.of("records", records, "total", total, "pageNo", pageNo, "pageSize", size));
    }
}
