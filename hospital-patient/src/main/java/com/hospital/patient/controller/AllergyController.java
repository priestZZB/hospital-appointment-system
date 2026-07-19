package com.hospital.patient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.patient.dto.AllergyDTO;
import com.hospital.patient.service.AllergyService;
import com.hospital.patient.vo.AllergyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 过敏史接口
 */
@Slf4j
@RestController
@RequestMapping("/api/patient/allergies")
@RequiredArgsConstructor
public class AllergyController {

    private final AllergyService allergyService;

    /** 查询过敏史列表 */
    @GetMapping
    public Result<List<AllergyVO>> list() {
        Long userId = UserContext.getUserId();
        return Result.ok(allergyService.listByUserId(userId));
    }

    /** 新增过敏史 */
    @AuditLog(value = "新增过敏史", operationType = "INSERT")
    @PostMapping
    public Result<AllergyVO> add(@Valid @RequestBody AllergyDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(allergyService.add(userId, dto));
    }

    /** 删除过敏史 */
    @AuditLog(value = "删除过敏史", operationType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        allergyService.delete(userId, id);
        return Result.ok();
    }
}
