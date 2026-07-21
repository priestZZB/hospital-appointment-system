package com.hospital.payment.mapper;

import com.hospital.payment.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内信通知表 Mapper
 */
@Mapper
public interface NotificationMapper {

    /** 插入 */
    int insert(Notification notification);

    /** 根据患者ID查询 */
    List<Notification> selectByPatientId(@Param("patientId") Long patientId);

    /** 标记已读 */
    int markAsRead(@Param("id") Long id);
}
