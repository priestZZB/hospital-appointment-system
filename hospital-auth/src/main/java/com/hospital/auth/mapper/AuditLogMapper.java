package com.hospital.auth.mapper;

import com.hospital.auth.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志表 Mapper
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 插入审计日志
     */
    int insert(AuditLog auditLog);

    /**
     * 分页查询审计日志（支持按操作人/操作类型/时间范围筛选）
     */
    List<AuditLog> pageQuery(@Param("userId") Long userId,
                             @Param("operationType") String operationType,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime,
                             @Param("offset") Integer offset,
                             @Param("limit") Integer limit);

    /**
     * 分页查询总数
     */
    long countPageQuery(@Param("userId") Long userId,
                        @Param("operationType") String operationType,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);
}
