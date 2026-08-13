package com.hospital.auth.dto;

import com.hospital.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQueryDTO extends PageDTO {

    /** 操作人 ID */
    private Long userId;

    /** 操作类型描述（模糊匹配） */
    private String operationType;

    /** 起始时间（yyyy-MM-dd HH:mm:ss） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间（yyyy-MM-dd HH:mm:ss） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
