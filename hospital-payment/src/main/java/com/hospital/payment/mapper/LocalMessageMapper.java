package com.hospital.payment.mapper;

import com.hospital.payment.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 本地消息表 Mapper
 */
@Mapper
public interface LocalMessageMapper {

    /** 插入 */
    int insert(LocalMessage message);

    /** 更新状态 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("failReason") String failReason);

    /** 查询失败或待发送的消息 */
    List<LocalMessage> selectFailedOrPending();
}
