package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 医嘱执行记录实体 */
@Data
public class OrderExecution implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long orderId;
    private Long admissionId;
    private Long executorId;
    private LocalDateTime executeTime;
    private String result;
    private LocalDateTime createTime;
}
