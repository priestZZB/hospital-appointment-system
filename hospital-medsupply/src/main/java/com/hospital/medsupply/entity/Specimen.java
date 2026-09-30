package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标本采集表实体（迭代8 C2）
 * <p>
 * 检验类（itemType=LAB）申请缴费后采集标本，核收状态机：
 * COLLECTED 已采集 → RECEIVED 核收 / REJECTED 拒收；RECEIVED → TESTING 检测中。
 *
 * @see V9__lis_pacs.sql — specimen 表 DDL
 */
@Data
public class Specimen implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 标本号（条码，SP+yyyyMMdd+6位序列） */
    private String specimenNo;

    /** 关联 exam_application.id */
    private Long applicationId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 标本类型: BLOOD-血 / URINE-尿 / STOOL-便 / SPUTUM-痰 / OTHER-其他 */
    private String specimenType;

    /** 容器（如 EDTA 抗凝管） */
    private String container;

    /** 采集部位 */
    private String collectSite;

    /** 状态: COLLECTED-已采集 / RECEIVED-核收 / REJECTED-拒收 / TESTING-检测中 */
    private String status;

    /** 采集人ID */
    private Long collectorId;

    /** 采集时间 */
    private LocalDateTime collectTime;

    /** 核收人ID（拒收时为拒收操作人） */
    private Long receiverId;

    /** 核收时间（拒收时为拒收时间） */
    private LocalDateTime receiveTime;

    /** 备注（拒收原因） */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
