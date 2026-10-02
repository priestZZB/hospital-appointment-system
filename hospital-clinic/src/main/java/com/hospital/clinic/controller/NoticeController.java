package com.hospital.clinic.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.clinic.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 院内公告接口（迭代13 L3，base=/api/clinic/notices）。
 * 发布/下线（管理员，权限 NOTICE_MANAGE）；分页浏览（登录即可）。
 */
@RestController
@RequestMapping("/api/clinic/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeMapper noticeMapper;

    /** 发布公告 */
    @PostMapping
    @AuditLog(value = "发布公告", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.NOTICE_MANAGE)
    public Result<String> publish(@RequestBody Map<String, Object> body) {
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "公告标题不能为空");
        }
        noticeMapper.insert(title, str(body.get("content")), str(body.get("noticeType")),
                UserContext.getUserId(), str(body.get("publisherName")));
        return Result.ok("公告已发布");
    }

    /** 公告分页（noticeType/status/keyword 筛选；登录即可浏览） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "noticeType", required = false) String noticeType,
                                            @RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> records = noticeMapper.selectPage(noticeType, status, keyword,
                (pageNo - 1) * pageSize, pageSize);
        long total = noticeMapper.countPage(noticeType, status, keyword);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", records);
        page.put("total", total);
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    /** 公告下线 */
    @PutMapping("/{id}/offline")
    @AuditLog(value = "公告下线", operationType = "UPDATE")
    @RequiresPermission(PermissionConstant.NOTICE_MANAGE)
    public Result<String> offline(@PathVariable("id") Long id) {
        if (noticeMapper.offline(id) == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已发布公告可下线");
        }
        return Result.ok("公告已下线");
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
