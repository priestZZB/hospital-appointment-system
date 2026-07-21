package com.hospital.payment.service;

import com.hospital.common.feign.PatientFeignClient;
import com.hospital.payment.entity.Notification;
import com.hospital.payment.mapper.NotificationMapper;
import com.hospital.payment.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 站内信通知服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final PatientFeignClient patientFeignClient;

    /**
     * 创建通知
     */
    @Transactional(rollbackFor = Exception.class)
    public void createNotification(Long patientId, String title, String content,
                                   String notifyType, String relatedId) {
        Notification notification = new Notification();
        notification.setPatientId(patientId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setNotifyType(notifyType);
        notification.setRelatedId(relatedId);
        notificationMapper.insert(notification);
        log.info("[通知] 通知已创建: patientId={}, title={}", patientId, title);
    }

    /**
     * 患者通知列表（由 userId 查询，内部转换 userId → patientId）
     */
    public List<NotificationVO> listByUserId(Long userId) {
        Long patientId;
        try {
            Map<String, Object> patientInfo = patientFeignClient.getByUserId(userId);
            if (patientInfo == null || patientInfo.isEmpty()) {
                return List.of();
            }
            patientId = toLong(patientInfo.get("id"));
            if (patientId == null) {
                return List.of();
            }
        } catch (Exception e) {
            log.warn("[通知] 查询患者信息失败: userId={}", userId, e);
            return List.of();
        }
        return listByPatient(patientId);
    }

    /**
     * 患者通知列表（直接按 patientId 查询）
     */
    private List<NotificationVO> listByPatient(Long patientId) {
        List<Notification> list = notificationMapper.selectByPatientId(patientId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 标记已读
     */
    public void markAsRead(Long id) {
        notificationMapper.markAsRead(id);
    }

    // ==================== 实体 → VO ====================

    private NotificationVO toVO(Notification n) {
        return NotificationVO.builder()
                .id(n.getId())
                .patientId(n.getPatientId())
                .title(n.getTitle())
                .content(n.getContent())
                .notifyType(n.getNotifyType())
                .relatedId(n.getRelatedId())
                .isRead(n.getIsRead())
                .readTime(n.getReadTime())
                .createTime(n.getCreateTime())
                .build();
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
