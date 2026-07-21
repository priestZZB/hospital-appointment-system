package com.hospital.payment.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信通知 VO
 */
@Data
@Builder
public class NotificationVO {

    private Long id;
    private Long patientId;
    private String title;
    private String content;
    private String notifyType;
    private String relatedId;
    private Integer isRead;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
