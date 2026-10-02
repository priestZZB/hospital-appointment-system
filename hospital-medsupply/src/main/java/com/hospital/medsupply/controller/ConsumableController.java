package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.mapper.ConsumableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 耗材管理接口（迭代14 L1，base=/api/medsupply/consumables）。
 * 字典维护 + 出入库（事务：流水 + 库存联动，出库校验库存不足）+ 低库存预警。
 */
@RestController
@RequestMapping("/api/medsupply/consumables")
@RequiredArgsConstructor
public class ConsumableController {

    private final ConsumableMapper consumableMapper;

    /** 新增耗材字典 */
    @PostMapping
    @AuditLog(value = "新增耗材字典", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.CONSUMABLE_MANAGE)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "耗材编码与名称不能为空");
        }
        if (consumableMapper.selectIdByCode(code) != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "耗材编码已存在");
        }
        consumableMapper.insert(code, name, str(body.get("specification")), str(body.get("unit")),
                toDouble(body.get("price")), body.get("stock") == null ? 0 : toInt(body.get("stock")),
                body.get("safetyStock") == null ? 0 : toInt(body.get("safetyStock")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", consumableMapper.selectIdByCode(code));
        data.put("code", code);
        return Result.ok(data);
    }

    /** 耗材分页（status/keyword） */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.CONSUMABLE_MANAGE)
    public Result<Map<String, Object>> list(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", consumableMapper.selectPage(status, keyword, (pageNo - 1) * pageSize, pageSize));
        page.put("total", consumableMapper.countPage(status, keyword));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 出入库（type = IN 入库 / OUT 出库） */
    @PostMapping("/{id}/stock")
    @Transactional
    @AuditLog(value = "耗材出入库", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CONSUMABLE_MANAGE)
    public Result<String> stock(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String type = str(body.get("type"));
        Integer quantity = toInt(body.get("quantity"));
        if (type == null || !List.of("IN", "OUT").contains(type)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "type 需为 IN 或 OUT");
        }
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "数量必须为正整数");
        }
        // 行锁锁定库存行，防止并发出入库
        if (consumableMapper.selectStockForUpdate(id) == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "耗材不存在");
        }
        int delta = "IN".equals(type) ? quantity : -quantity;
        if (consumableMapper.adjustStock(id, delta, "OUT".equals(type)) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "库存不足，出库失败");
        }
        consumableMapper.insertRecord(id, type, quantity, UserContext.getUserId(),
                "user-" + UserContext.getUserId(), str(body.get("remark")));
        return Result.ok("IN".equals(type) ? "入库成功" : "出库成功");
    }

    /** 出入库流水分页 */
    @GetMapping("/records")
    @RequiresPermission(PermissionConstant.CONSUMABLE_MANAGE)
    public Result<Map<String, Object>> records(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", consumableMapper.selectRecordPage((pageNo - 1) * pageSize, pageSize));
        page.put("total", consumableMapper.countRecordPage());
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 低库存预警 */
    @GetMapping("/low-stock")
    @RequiresPermission(PermissionConstant.CONSUMABLE_MANAGE)
    public Result<List<Map<String, Object>>> lowStock() {
        return Result.ok(consumableMapper.selectLowStock());
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private Integer toInt(Object v) {
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }

    private Double toDouble(Object v) {
        return v == null ? null : Double.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
