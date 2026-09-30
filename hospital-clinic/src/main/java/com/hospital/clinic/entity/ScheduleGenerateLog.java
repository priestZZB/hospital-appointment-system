package com.hospital.clinic.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动排班 Job 执行日志实体
 *
 * @see <a href="classpath:db/migration/V10__outpatient_enhance.sql">schedule_generate_log 表 DDL（迭代9 A8）</a>
 */
@Data
public class ScheduleGenerateLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 本次生成目标出诊日期 */
    private LocalDate bizDate;

    /** 本次实际新建排班数量 */
    private Integer createdCount;

    /** 执行结果说明（跳过数量、失败原因等） */
    private String message;

    /** 创建时间 */
    private LocalDateTime createTime;
}
