package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.service.CloudLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 云影像链接接口（D6 云影像分享）
 * <p>
 * 技师/医生为已完成的检查生成 24h 云影像链接（二维码内容为 url）；
 * 患者凭 code 查看影像序列列表与已发布报告。
 * <p>
 * 安全模型（毕设简化）：/view 挂 MEDSUPPLY_CLOUDLINK_VIEW 权限（患者已种），
 * 未登录访问由网关统一拦截；不另设公开匿名端点。
 */
@RestController
@RequestMapping("/api/medsupply/cloud")
@RequiredArgsConstructor
public class CloudLinkController {

    private final CloudLinkService cloudLinkService;

    /** 生成云影像链接（返回 {code, url}，url 形如 /cloud-view/{code}） */
    @AuditLog(value = "云影像链接生成", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_CLOUDLINK_CREATE)
    @PostMapping("/{applicationId}/link")
    public Result<Map<String, Object>> createLink(@PathVariable("applicationId") Long applicationId) {
        return Result.ok(cloudLinkService.createLink(applicationId, UserContext.getUserId()));
    }

    /** 凭 code 查看云影像（影像列表始终返回；report 仅 PUBLISHED 时返回内容） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_CLOUDLINK_VIEW)
    @GetMapping("/view/{code}")
    public Result<Map<String, Object>> view(@PathVariable("code") String code) {
        return Result.ok(cloudLinkService.resolve(code));
    }
}
