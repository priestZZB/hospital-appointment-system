package com.hospital.payment.controller;

import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.payment.service.NotificationService;
import com.hospital.payment.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内信通知接口
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知列表 */
    @GetMapping
    public Result<List<NotificationVO>> list() {
        Long userId = UserContext.getUserId();
        return Result.ok(notificationService.listByUserId(userId));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.ok();
    }
}
