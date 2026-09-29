package com.hospital.inpatient.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 医嘱执行记录 Mapper（注解式） */
@Mapper
public interface OrderExecutionMapper {

    @Insert("INSERT INTO order_execution (order_id, admission_id, executor_id, execute_time, result, create_time) " +
            "VALUES (#{orderId}, #{admissionId}, #{executorId}, SYSDATE, #{result}, SYSDATE)")
    int insert(@Param("orderId") Long orderId,
               @Param("admissionId") Long admissionId,
               @Param("executorId") Long executorId,
               @Param("result") String result);

    @Select("SELECT COUNT(*) FROM order_execution WHERE order_id = #{orderId}")
    long countByOrder(@Param("orderId") Long orderId);
}
