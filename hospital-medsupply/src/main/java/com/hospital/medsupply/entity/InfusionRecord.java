package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 输液执行记录表实体
 * <p>
 * 护士每次执行输液操作时生成一条记录。
 *
 * @see V5__infusion_and_exam_exec.sql — infusion_record 表 DDL
 */
@Data
public class InfusionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联 infusion_order.id */
    private Long infusionOrderId;

    /** 执行类型：SKIN_TEST-皮试 / PREPARE-配液 / START-开始输液 / END-结束输液 / OBSERVE-观察记录 */
    private String recordType;

    /** 执行内容/观察记录 */
    private String recordContent;

    /** 皮试结果：NEGATIVE-阴性 / POSITIVE-阳性 */
    private String skinTestResult;

    /** 滴速（滴/分钟） */
    private Integer dropRate;

    /** 执行护士ID */
    private Long operatorId;

    /** 执行护士姓名 */
    private String operatorName;

    /** 执行时间 */
    private LocalDateTime createTime;
}
