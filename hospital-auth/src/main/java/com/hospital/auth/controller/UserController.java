package com.hospital.auth.controller;

import com.hospital.auth.dto.UserPageQueryDTO;
import com.hospital.auth.service.UserService;
import com.hospital.auth.vo.UserVO;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public Result<UserService.PageResult<UserVO>> pageQuery(@Valid UserPageQueryDTO dto) {
        return Result.ok(userService.pageQuery(dto));
    }

    /**
     * 变更用户状态（启用/禁用）
     */
    @AuditLog(value = "变更用户状态", operationType = "UPDATE")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        userService.updateStatus(id, status);
        return Result.ok();
    }
}
