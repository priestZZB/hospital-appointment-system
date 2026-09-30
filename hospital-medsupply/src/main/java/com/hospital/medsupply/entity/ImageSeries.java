package com.hospital.medsupply.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 影像序列表实体（D2 影像序列，检查号关联 JPG 序列 → MinIO）
 * <p>
 * 一次检查产生的同模态影像文件归为一个序列上传 MinIO，
 * object_keys 为 CLOB，存放 MinIO 对象 key 的 JSON 数组（保持顺序）。
 *
 * @see V9__lis_pacs.sql — image_series 表 DDL
 */
@Data
public class ImageSeries implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 影像号（唯一，IM+yyyyMMdd+6 位） */
    private String seriesNo;

    /** 关联 exam_application.id */
    private Long applicationId;

    /** 关联 patient_db.patient.id */
    private Long patientId;

    /** 检查类别: DX摄影/CT/MR/US超声/ENDO内镜/PATH病理/ECG心电 */
    private String modality;

    /** 序列描述 */
    private String description;

    /** 影像张数 */
    private Integer imageCount;

    /** MinIO 对象 key 的 JSON 数组（如 ["pacs/1/IM20260101000001/0.jpg", ...]） */
    private String objectKeys;

    /** 上传人 ID */
    private Long uploadBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
