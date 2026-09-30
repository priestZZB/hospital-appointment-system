package com.hospital.clinic.controller;

import com.hospital.clinic.dto.IcdDictSaveDTO;
import com.hospital.clinic.service.IcdDictService;
import com.hospital.clinic.vo.IcdDictVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ICD-10 诊断字典接口（迭代9 J3）
 * <p>
 * 查询（分诊/诊疗录入联想）：分页 keyword 匹配 code/name、category 筛选、常用优先；
 * 管理（管理员）：字典 CRUD。病历提交时由 ConsultationService 校验诊断编码命中字典。
 */
@Slf4j
@RestController
@RequestMapping("/api/clinic/icd")
@RequiredArgsConstructor
public class IcdDictController {

    private final IcdDictService icdDictService;

    /** 分页查询（keyword 匹配 code/name、category 筛选、常用优先） */
    @RequiresPermission(PermissionConstant.CLINIC_ICD_QUERY)
    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.ok(icdDictService.page(keyword, category, pageNo, pageSize));
    }

    /** 章节分类列表（筛选下拉） */
    @RequiresPermission(PermissionConstant.CLINIC_ICD_QUERY)
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.ok(icdDictService.categories());
    }

    /** 字典详情 */
    @RequiresPermission(PermissionConstant.CLINIC_ICD_QUERY)
    @GetMapping("/{id}")
    public Result<IcdDictVO> getById(@PathVariable Long id) {
        return Result.ok(icdDictService.getById(id));
    }

    /** 新增字典条目（管理员） */
    @AuditLog(value = "新增ICD字典条目", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.CLINIC_ICD_MANAGE)
    @PostMapping
    public Result<IcdDictVO> create(@Valid @RequestBody IcdDictSaveDTO dto) {
        checkAdmin();
        return Result.ok(icdDictService.create(dto));
    }

    /** 编辑字典条目（管理员） */
    @AuditLog(value = "编辑ICD字典条目", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.CLINIC_ICD_MANAGE)
    @PutMapping("/{id}")
    public Result<IcdDictVO> update(@PathVariable Long id, @Valid @RequestBody IcdDictSaveDTO dto) {
        checkAdmin();
        return Result.ok(icdDictService.update(id, dto));
    }

    /** 删除字典条目（管理员） */
    @AuditLog(value = "删除ICD字典条目", operationType = "DELETE")
    @RequiresPermission(PermissionConstant.CLINIC_ICD_MANAGE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkAdmin();
        icdDictService.delete(id);
        return Result.ok();
    }

    private void checkAdmin() {
        if (!UserContext.isAdminOrSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅管理员可管理 ICD 字典");
        }
    }
}
