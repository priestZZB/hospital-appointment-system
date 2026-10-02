package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.medsupply.mapper.EquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备台账接口（迭代14 L2，base=/api/medsupply/equipments）。
 * 台账登记 + 状态流转（USING 使用中 / IDLE 闲置 / REPAIR 维修 / SCRAP 报废）+ 维保登记。
 */
@RestController
@RequestMapping("/api/medsupply/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private static final List<String> STATUSES = List.of("USING", "IDLE", "REPAIR", "SCRAP");

    private final EquipmentMapper equipmentMapper;

    /** 新增设备 */
    @PostMapping
    @AuditLog(value = "新增设备台账", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.EQUIPMENT_MANAGE)
    public Result<String> create(@RequestBody Map<String, Object> body) {
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "设备编码与名称不能为空");
        }
        equipmentMapper.insert(code, name, str(body.get("model")), str(body.get("location")),
                str(body.get("buyDate")), str(body.get("remark")));
        return Result.ok("设备已登记");
    }

    /** 设备分页（status/keyword） */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.EQUIPMENT_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", equipmentMapper.selectPage(status, keyword, (pageNo - 1) * pageSize, pageSize));
        page.put("total", equipmentMapper.countPage(status, keyword));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 设备详情 */
    @GetMapping("/{id}")
    @RequiresPermission(PermissionConstant.EQUIPMENT_MANAGE)
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        Map<String, Object> equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "设备不存在");
        }
        return Result.ok(equipment);
    }

    /** 状态变更（SCRAP 为终态，报废设备不可再转出） */
    @PutMapping("/{id}/status")
    @AuditLog(value = "设备状态变更", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.EQUIPMENT_MANAGE)
    public Result<String> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        if (status == null || !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "status 需为 USING/IDLE/REPAIR/SCRAP");
        }
        if (equipmentMapper.updateStatus(id, status) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "设备不存在或已报废（SCRAP 为终态）");
        }
        return Result.ok("状态已更新");
    }

    /** 维保登记（恢复 IDLE 并记录维保日期；已报废设备不可维保） */
    @PutMapping("/{id}/maintain")
    @AuditLog(value = "设备维保登记", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.EQUIPMENT_MANAGE)
    public Result<String> maintain(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String maintainDate = str(body.get("maintainDate"));
        if (maintainDate == null || maintainDate.isBlank()) {
            maintainDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        try {
            LocalDate.parse(maintainDate);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "maintainDate 格式应为 yyyy-MM-dd");
        }
        if (equipmentMapper.maintain(id, maintainDate) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "设备不存在或已报废（SCRAP 为终态）");
        }
        return Result.ok("维保已登记");
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
