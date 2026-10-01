package com.hospital.inpatient.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 临床路径模板实体（迭代12 J1） */
@Data
public class PathTemplate implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String pathCode;
    private String pathName;
    private String diseaseName;
    /** 标准住院日 */
    private Integer standardDays;
    /** 费用目标 */
    private BigDecimal totalEstimate;
    /** 阶段医嘱项 JSON：[{"day":1,"items":[...]}] */
    private String itemJson;
    /** 1 启用 | 0 停用 */
    private Integer status;
    private LocalDateTime createTime;
}
